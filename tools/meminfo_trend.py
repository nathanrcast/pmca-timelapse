#!/usr/bin/env python3
"""Reports heap growth over a soak from soak-meminfo.log (tools/soak_meminfo.sh).

Reads the allocated native and dalvik heap (kB) from each sample, handles the Android 2.3 table
("allocated:" row) and the later one ("Native Heap" / "Dalvik Heap" rows, Heap Alloc column).
Exit 1 if a heap's least-squares trend over the run, or its last-quartile minimum minus its
first-quartile maximum, exceeds --threshold-kb (a single GC dip does not hide a leak); exit 2 if
the log has too few samples to judge.
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


def trend(pts):
    """(growth over the run in kB from the least-squares slope, kB/h or None, last-quartile min - first-quartile max)."""
    vals = [v for _, v in pts]
    n = len(vals)
    first, last = pts[0][0], pts[-1][0]
    hours = (last - first).total_seconds() / 3600.0 if first and last else 0.0
    if hours > 0 and all(t for t, _ in pts):
        xs = [(t - first).total_seconds() / 3600.0 for t, _ in pts]
        span = hours
    else:
        xs = list(range(n))
        span = float(n - 1)
    mx, my = sum(xs) / n, sum(vals) / n
    sxx = sum((x - mx) ** 2 for x in xs)
    slope = sum((x - mx) * (v - my) for x, v in zip(xs, vals)) / sxx if sxx else 0.0
    q = max(1, n // 4)
    return slope * span, (slope if hours > 0 else None), min(vals[-q:]) - max(vals[:q])


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
        fitted, rate, quartile = trend(pts)
        bad = fitted > threshold_kb or quartile > threshold_kb
        failed = failed or bad
        lines.append("%s: %d samples, %d -> %d kB, trend %+.0f kB%s, last-quartile min - first-quartile max %+d kB, "
                     "min %d max %d, %s" % (
                         heap, len(vals), vals[0], vals[-1], fitted,
                         " (%.0f kB/h)" % rate if rate is not None else "", quartile, min(vals), max(vals),
                         "GROWTH" if bad else "ok"))
    return lines, failed, judged


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("log", help="soak-meminfo.log")
    ap.add_argument("--threshold-kb", type=int, default=2048,
                    help="growth that counts as a leak (default 2048)")
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
