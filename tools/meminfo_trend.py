#!/usr/bin/env python3
"""Reports heap growth over a soak from soak-meminfo.log (tools/soak_meminfo.sh).

Reads the allocated native and dalvik heap (kB) from each sample, handles the Android 2.3 table
("allocated:" row) and the later one ("Native Heap" / "Dalvik Heap" rows, Heap Alloc column).
Exit 1 if a heap never shrank across the run and grew by more than --threshold-kb; exit 2 if the
log has too few samples to judge.
"""
import argparse
import re
import sys
from datetime import datetime, timezone

STAMP = re.compile(r"^=== (\S+) ===\s*$")
NUM = re.compile(r"-?\d+")
HEAPS = ("native", "dalvik")


def parse_stamp(text):
    try:
        return datetime.strptime(text, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError:
        return None


def parse_sample(lines):
    """{'native': kB, 'dalvik': kB} from one dumpsys block; missing keys when unreadable."""
    out = {}
    for line in lines:
        s = line.strip()
        low = s.lower()
        nums = [int(n) for n in NUM.findall(s)]
        if low.startswith("allocated:"):
            # 2.3 columns: native dalvik other total (other is N/A)
            if len(nums) >= 2:
                out["native"], out["dalvik"] = nums[0], nums[1]
        elif low.startswith("native heap") and "native" not in out and len(nums) >= 3:
            out["native"] = nums[-2]
        elif low.startswith("dalvik heap") and "dalvik" not in out and len(nums) >= 3:
            out["dalvik"] = nums[-2]
    return out


def parse_log(text):
    samples = []
    stamp = None
    block = []

    def flush():
        if stamp is not None:
            samples.append((stamp, parse_sample(block)))

    for line in text.splitlines():
        m = STAMP.match(line)
        if m:
            flush()
            stamp, block = parse_stamp(m.group(1)), []
        else:
            block.append(line)
    flush()
    return samples


def series(samples, heap):
    return [(t, s[heap]) for t, s in samples if heap in s]


def monotonic_growth(values):
    return all(b >= a for a, b in zip(values, values[1:]))


def analyse(samples, threshold_kb, min_samples):
    """Returns (report lines, failed, judged)."""
    lines = []
    failed = False
    judged = False
    for heap in HEAPS:
        pts = series(samples, heap)
        if len(pts) < min_samples:
            lines.append("%s: %d usable samples (need %d)" % (heap, len(pts), min_samples))
            continue
        judged = True
        vals = [v for _, v in pts]
        growth = vals[-1] - vals[0]
        hours = None
        if pts[0][0] and pts[-1][0]:
            hours = (pts[-1][0] - pts[0][0]).total_seconds() / 3600.0
        rate = " (%.0f kB/h)" % (growth / hours) if hours else ""
        mono = monotonic_growth(vals)
        bad = mono and growth > threshold_kb
        failed = failed or bad
        lines.append("%s: %d samples, %d -> %d kB, growth %+d kB%s, min %d max %d, %s" % (
            heap, len(vals), vals[0], vals[-1], growth, rate, min(vals), max(vals),
            "MONOTONIC GROWTH" if bad else "ok"))
    return lines, failed, judged


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("log", help="soak-meminfo.log")
    ap.add_argument("--threshold-kb", type=int, default=2048,
                    help="growth that counts as a leak when a heap never shrinks (default 2048)")
    ap.add_argument("--min-samples", type=int, default=4, help="samples needed per heap (default 4)")
    args = ap.parse_args(argv)
    try:
        with open(args.log, encoding="utf-8", errors="replace") as f:
            text = f.read()
    except OSError as e:
        print("cannot read %s: %s" % (args.log, e), file=sys.stderr)
        return 2
    samples = parse_log(text)
    lines, failed, judged = analyse(samples, args.threshold_kb, args.min_samples)
    print("%d samples" % len(samples))
    for line in lines:
        print(line)
    if not judged:
        print("not enough data to judge", file=sys.stderr)
        return 2
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
