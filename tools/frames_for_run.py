#!/usr/bin/env python3
"""List the JPEGs under a directory, optionally only those shot by one run in LOG.CSV.

Python 3 stdlib only; run as `python3 -I tools/frames_for_run.py DIR [--log LOG.CSV --run RUN_ID]`.

A frame belongs to a run when its mtime falls inside [FIRE - window, capture_done + window] of any FIRE row
of that run (capture_done comes from the SLOT_DONE row of the same slot and bracket_idx, else the FIRE time).
Row times are uptime ms; the run's `# uptime_to_wall_ms` header converts them to wall clock.
Prints one path per line, sorted by file name then path. Exit 2 on bad input, 3 when no frame matches.
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
                found.append(os.path.join(base, name))
    return sorted(found, key=lambda p: (os.path.basename(p), p))


def fire_windows(run, offset_s=0.0):
    """Wall-clock [start, end] seconds for every FIRE row of the run."""
    wall_ms = analyze_log.parse_int(run.meta.get("uptime_to_wall_ms"))
    if wall_ms is None:
        raise ValueError("run %s has no uptime_to_wall_ms header" % run.run_id)
    done = {}
    for rec in run.rows:
        if rec["result"] == "SLOT_DONE" and rec["capture_done_ms"].strip():
            done[(rec["slot"], rec["bracket_idx"])] = int(rec["capture_done_ms"])
    windows = []
    for rec in run.rows:
        if rec["result"] != "FIRE":
            continue
        start = analyze_log.parse_int(rec["actual_ms"])
        if start is None:
            start = analyze_log.parse_int(rec["scheduled_ms"])
        if start is None:
            continue
        end = max(start, done.get((rec["slot"], rec["bracket_idx"]), start))
        windows.append(((start + wall_ms) / 1000.0 + offset_s, (end + wall_ms) / 1000.0 + offset_s))
    return windows


def frames_for_run(files, run, window_s=2.0, offset_s=0.0, mtime=os.path.getmtime):
    windows = fire_windows(run, offset_s)
    matched = []
    for path in files:
        t = mtime(path)
        if any(lo - window_s <= t <= hi + window_s for lo, hi in windows):
            matched.append(path)
    return matched


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
            files = frames_for_run(files, chosen[0], args.window_s, args.offset_s)
        except ValueError as e:
            print("frames_for_run: %s" % e, file=sys.stderr)
            return 2
        if not files:
            return 3
    for path in files:
        print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
