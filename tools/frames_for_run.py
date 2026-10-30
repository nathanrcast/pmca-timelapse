#!/usr/bin/env python3
"""List the JPEGs under a directory, optionally only those shot by one run in LOG.CSV.

Python 3 stdlib only; run as `python3 -I tools/frames_for_run.py DIR [--log LOG.CSV --run RUN_ID]`.

A run's frames are every JPEG whose mtime lies in the run's wall-clock span: first FIRE minus slack to last
SLOT_DONE (else FIRE) plus slack. Slack is max(window, 60 s) plus 1 % of the run, which absorbs the uptime clock
stopping in deep sleep and bracket frames written after the last callback. Row times are uptime ms, converted with
the run's `# uptime_to_wall_ms` header. The count is compared with the FIRE rows and a mismatch is warned about.
mtimes must survive the copy off the card (cp -p, rsync -t); use --offset-s for a time-zone or clock mismatch.
Prints absolute paths, one per line, ordered by (folder, file name). Exit 2 on bad input, 3 when nothing matches.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import analyze_log  # noqa: E402

EXTENSIONS = (".jpg", ".jpeg")


def list_jpegs(root):
    found = []
    for base, _dirs, names in os.walk(root):
        for name in names:
            if name.lower().endswith(EXTENSIONS):
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


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("dir")
    ap.add_argument("--log", help="LOG.CSV (required with --run or --list-runs)")
    ap.add_argument("--run", help="run_id to restrict to")
    ap.add_argument("--list-runs", action="store_true", help="print the run_ids in --log and exit")
    ap.add_argument("--window-s", type=float, default=2.0, help="mtime tolerance in seconds (default 2)")
    ap.add_argument("--offset-s", type=float, default=0.0,
                    help="add to log wall times, e.g. 3600 if the card clock is an hour ahead")
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
    files = list_jpegs(args.dir)
    if args.run:
        chosen = [r for r in runs if r.run_id == args.run]
        if not chosen:
            print("frames_for_run: run %s not in %s" % (args.run, args.log), file=sys.stderr)
            return 2
        try:
            files, fires = frames_for_run(files, chosen[0], args.window_s, args.offset_s)
        except ValueError as e:
            print("frames_for_run: %s" % e, file=sys.stderr)
            return 2
        if not files:
            print("frames_for_run: no frame has a file time inside run %s; copy the card with timestamps "
                  "(cp -p, rsync -t) or try --offset-s for a clock or time-zone difference" % args.run,
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
