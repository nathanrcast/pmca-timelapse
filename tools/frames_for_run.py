#!/usr/bin/env python3
"""List the JPEGs under a directory, optionally only those shot by one run in LOG.CSV.

Python 3 stdlib only; run as `python3 -I tools/frames_for_run.py DIR [--log LOG.CSV --run RUN_ID]`.

A run's frames are every JPEG whose mtime lies in the run's wall-clock span: first FIRE minus slack to last
SLOT_DONE (else FIRE) plus slack. Slack is max(window, 60 s) plus 1 % of the run, which absorbs the uptime clock
stopping in deep sleep and bracket frames written after the last callback. Row times are uptime ms, converted with
the run's `# uptime_to_wall_ms` header. The count is compared with the FIRE rows and a mismatch is warned about.
A frame's time is its EXIF DateTimeOriginal when the file has one (JPEG APP1 or a TIFF-based RAW such as ARW; stdlib
parser), else its mtime. EXIF is the camera's own local clock time, so it is free of the host's time zone; it is
matched against the log's wall times shifted by the run's `# utc_offset_min` header. mtimes are what exFAT local time
looks like to the host's zone (often hours off): they must survive the copy off the card (cp -p, rsync -t), and
--offset-s corrects a time-zone or clock mismatch. One run uses one time base: if any candidate frame (one whose mtime
is within a day of the run) has no readable EXIF, mtime is used for the whole run and a warning says so. --no-exif forces mtime; --include-raw also lists .arw files.
Prints absolute paths, one per line, ordered by (folder, file name). Exit 2 on bad input, 3 when nothing matches.
"""
import argparse
import calendar
import os
import struct
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analyze_log  # noqa: E402

EXTENSIONS = (".jpg", ".jpeg")
RAW_EXTENSIONS = (".arw",)

TAG_EXIF_IFD = 0x8769
TAG_DATETIME_ORIGINAL = 0x9003
TYPE_SIZES = {1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 7: 1, 9: 4, 10: 8}


def _read_tiff_datetime(f, base):
    """DateTimeOriginal from a TIFF structure that starts at file offset `base`, or None."""
    f.seek(base)
    head = f.read(8)
    if len(head) < 8 or head[:2] not in (b"II", b"MM"):
        return None
    e = "<" if head[:2] == b"II" else ">"
    if struct.unpack(e + "H", head[2:4])[0] != 42:
        return None

    def find(ifd_off, tag):
        f.seek(base + ifd_off)
        raw = f.read(2)
        if len(raw) < 2:
            return None
        count = struct.unpack(e + "H", raw)[0]
        entries = f.read(12 * count)
        if len(entries) < 12 * count:
            return None
        for i in range(count):
            t, typ, n, val = struct.unpack(e + "HHI4s", entries[12 * i:12 * i + 12])
            if t == tag:
                return typ, n, val
        return None

    exif = find(struct.unpack(e + "I", head[4:8])[0], TAG_EXIF_IFD)
    if exif is None:
        return None
    dt = find(struct.unpack(e + "I", exif[2])[0], TAG_DATETIME_ORIGINAL)
    if dt is None or dt[0] != 2 or dt[1] < 19:
        return None
    f.seek(base + struct.unpack(e + "I", dt[2])[0])
    text = f.read(19)
    try:
        return calendar.timegm(time.strptime(text.decode("ascii"), "%Y:%m:%d %H:%M:%S"))
    except (UnicodeDecodeError, ValueError):
        return None


def exif_time(path):
    """EXIF DateTimeOriginal as seconds, reading the camera's local time as if it were UTC; None if unreadable."""
    try:
        with open(path, "rb") as f:
            magic = f.read(4)
            if magic[:2] in (b"II", b"MM"):
                return _read_tiff_datetime(f, 0)
            if magic[:2] != b"\xff\xd8":
                return None
            pos = 2
            while True:
                f.seek(pos)
                hdr = f.read(4)
                if len(hdr) < 4 or hdr[0] != 0xFF:
                    return None
                marker, length = hdr[1], struct.unpack(">H", hdr[2:4])[0]
                if marker == 0xE1 and f.read(6) == b"Exif\x00\x00":
                    return _read_tiff_datetime(f, pos + 10)
                if marker in (0xD9, 0xDA):
                    return None
                pos += 2 + length
    except (OSError, struct.error):
        return None


def list_jpegs(root, extensions=EXTENSIONS):
    found = []
    for base, _dirs, names in os.walk(root):
        for name in names:
            if name.lower().endswith(extensions):
                found.append((os.path.abspath(base), name))
    found.sort()
    return [os.path.join(d, n) for d, n in found]


def run_span(run, window_s=2.0, offset_s=0.0):
    """Returns (lo, hi, fire_count): wall-clock seconds of the run's span and its number of FIRE rows."""
    wall_ms = analyze_log.parse_int(run.meta.get("uptime_to_wall_ms"))
    if wall_ms is None:
        raise ValueError("run %s has no uptime_to_wall_ms header" % run.run_id)
    first = last = None
    fires = 0
    for rec in run.rows:
        if rec["result"] == "FIRE":
            fires += 1
            cols = ("actual_ms", "scheduled_ms")
        elif rec["result"] == "SLOT_DONE":
            cols = ("capture_done_ms",)
        else:
            continue
        t = None
        for col in cols:
            t = analyze_log.parse_int(rec[col])
            if t is not None:
                break
        if t is None:
            continue
        first = t if first is None else min(first, t)
        last = t if last is None else max(last, t)
    if first is None:
        raise ValueError("run %s has no FIRE rows" % run.run_id)
    slack = max(window_s, 60.0) + 0.01 * (last - first) / 1000.0
    return (first + wall_ms) / 1000.0 - slack + offset_s, (last + wall_ms) / 1000.0 + slack + offset_s, fires


def frames_for_run(files, run, window_s=2.0, offset_s=0.0, mtime=os.path.getmtime):
    """Returns (matched files in the given order, FIRE row count)."""
    lo, hi, fires = run_span(run, window_s, offset_s)
    return [p for p in files if lo <= mtime(p) <= hi], fires


CANDIDATE_SLACK_S = 26 * 3600  # any time-zone offset between the card's local time and the host's


def time_fn(files, run, window_s=2.0, offset_s=0.0, use_exif=True, warn=None):
    """Frame-time function for one run, on a single time base.

    EXIF (converted to the log's UTC base) when every candidate frame has it, else mtime for all of them."""
    if use_exif:
        try:
            lo, hi, _fires = run_span(run, window_s, offset_s)
        except ValueError:
            lo = hi = None
        if lo is not None:
            near = []
            for p in files:
                try:
                    m = os.path.getmtime(p)
                except OSError:
                    continue
                if lo - CANDIDATE_SLACK_S <= m <= hi + CANDIDATE_SLACK_S:
                    near.append(p)
            exif = {p: exif_time(p) for p in near}
            if near and all(t is not None for t in exif.values()):
                utc_offset_s = 60 * (analyze_log.parse_int(run.meta.get("utc_offset_min")) or 0)
                return lambda path: (exif[path] if path in exif else exif_time(path) or 0) - utc_offset_s
            if near and warn and any(t is not None for t in exif.values()):
                missing = sum(1 for t in exif.values() if t is None)
                warn("%d of %d candidate frames have no EXIF DateTimeOriginal; matching the whole run on file "
                     "mtime (use --offset-s for a host time-zone difference)" % (missing, len(near)))
    return os.path.getmtime


def warn(msg):
    print("frames_for_run: " + msg, file=sys.stderr)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("dir")
    ap.add_argument("--log", help="LOG.CSV (required with --run or --list-runs)")
    ap.add_argument("--run", help="run_id to restrict to")
    ap.add_argument("--list-runs", action="store_true", help="print the run_ids in --log and exit")
    ap.add_argument("--window-s", type=float, default=2.0, help="mtime tolerance in seconds (default 2)")
    ap.add_argument("--offset-s", type=float, default=0.0,
                    help="add to log wall times, e.g. 3600 if the card clock is an hour ahead")
    ap.add_argument("--no-exif", action="store_true", help="match on file mtime only")
    ap.add_argument("--include-raw", action="store_true", help="also list .arw files")
    args = ap.parse_args(argv)
    runs = []
    if args.log:
        try:
            with open(args.log, encoding="utf-8", errors="replace") as f:
                runs = analyze_log.read_runs(f)
        except (OSError, ValueError) as e:
            print("frames_for_run: %s" % e, file=sys.stderr)
            return 2
    if args.list_runs:
        for run in runs:
            print(run.run_id)
        return 0
    if not os.path.isdir(args.dir):
        print("frames_for_run: not a directory: %s" % args.dir, file=sys.stderr)
        return 2
    files = list_jpegs(args.dir, EXTENSIONS + RAW_EXTENSIONS if args.include_raw else EXTENSIONS)
    if args.run:
        chosen = [r for r in runs if r.run_id == args.run]
        if not chosen:
            print("frames_for_run: run %s not in %s" % (args.run, args.log), file=sys.stderr)
            return 2
        try:
            files, fires = frames_for_run(files, chosen[0], args.window_s, args.offset_s,
                                       time_fn(files, chosen[0], args.window_s, args.offset_s,
                                                not args.no_exif, warn))
        except ValueError as e:
            print("frames_for_run: %s" % e, file=sys.stderr)
            return 2
        if not files:
            print("frames_for_run: no frame has a file time inside run %s; copy the card with timestamps "
                  "(cp -p, rsync -t) or try --offset-s for a clock or time-zone difference "
                  "(exFAT mtimes can be hours off the camera's EXIF time)" % args.run,
                  file=sys.stderr)
            return 3
        if len(files) != fires:
            print("frames_for_run: run %s has %d FIRE rows but %d frames match; check for skipped or lost "
                  "shots, other shoots in the same time span, or bracket counts" % (args.run, fires, len(files)),
                  file=sys.stderr)
    for path in files:
        print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
