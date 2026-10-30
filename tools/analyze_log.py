#!/usr/bin/env python3
"""Summarise TIMELPS/LOG.CSV per run. Python 3 stdlib only; run as `python3 -I tools/analyze_log.py LOG.CSV`.

Exit status: 0 clean, 1 drift above --max-drift-ms or unexplained skips, 2 unreadable input.
"""
import argparse
import csv
import sys

COLUMNS = ["run_id", "slot", "scheduled_ms", "actual_ms", "capture_done_ms", "result", "skip_reason",
           "ss", "iso", "ev", "bracket_idx", "battery_pct", "free_mb"]


class Run:
    def __init__(self, run_id):
        self.run_id = run_id
        self.meta = {}
        self.rows = []


def parse_int(value):
    value = (value or "").strip()
    return int(value) if value else None


def read_runs(lines):
    runs = {}
    order = []
    current = None
    columns = None
    for number, row in enumerate(csv.reader(lines), 1):
        if not row or not "".join(row).strip():
            continue
        first = row[0]
        if first.startswith("#"):
            key, _, value = ",".join(row)[1:].strip().partition("=")
            key, value = key.strip(), value.strip()
            if key == "run_id":
                if value not in runs:
                    runs[value] = Run(value)
                    order.append(value)
                current = runs[value]
            elif current is not None:
                current.meta[key] = value
            continue
        if first == "run_id":
            columns = row
            continue
        if columns is None:
            columns = COLUMNS
        if len(row) != len(columns):
            raise ValueError("line %d: expected %d fields, got %d" % (number, len(columns), len(row)))
        rec = dict(zip(columns, row))
        run_id = rec["run_id"]
        if run_id not in runs:
            runs[run_id] = Run(run_id)
            order.append(run_id)
        runs[run_id].rows.append(rec)
    return [runs[r] for r in order]


def percentile(values, pct):
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, -(-len(ordered) * pct // 100))
    return ordered[int(rank) - 1]


def fmt(value):
    if value is None:
        return "n/a"
    if isinstance(value, float):
        return ("%.1f" % value)
    return str(value)


def analyze(run):
    """Returns (stats dict, list of problems)."""
    drifts, durations, skips = [], [], {}
    unexplained = 0
    fires, battery = [], []
    last_fire = {}
    completed = 0
    for rec in run.rows:
        result = rec["result"]
        slot = parse_int(rec["slot"])
        actual = parse_int(rec["actual_ms"])
        sched = parse_int(rec["scheduled_ms"])
        bracket = parse_int(rec["bracket_idx"])
        pct = parse_int(rec["battery_pct"])
        if result == "FIRE" and actual is not None:
            if sched is not None and (bracket is None or bracket == 0):
                drifts.append(actual - sched)
                fires.append(actual)
            last_fire[slot] = actual
            if pct is not None:
                battery.append((actual, pct))
        elif result == "SLOT_DONE":
            completed += 1
            done = parse_int(rec["capture_done_ms"])
            if done is not None and slot in last_fire:
                durations.append(done - last_fire[slot])
        elif result == "SKIP":
            reason = rec["skip_reason"].strip()
            if not reason:
                unexplained += 1
                reason = "(none)"
            skips[reason] = skips.get(reason, 0) + 1
    stats = {
        "fired": len(fires),
        "completed": completed,
        "drift_max": max(drifts) if drifts else None,
        "drift_mean": sum(drifts) / float(len(drifts)) if drifts else None,
        "drift_p95": percentile(drifts, 95),
        "skips": skips,
        "unexplained": unexplained,
        "shots_per_min": None,
        "capture_p95": percentile(durations, 95),
        "battery_first": battery[0][1] if battery else None,
        "battery_last": battery[-1][1] if battery else None,
        "battery_per_hour": None,
    }
    if len(fires) > 1 and fires[-1] > fires[0]:
        stats["shots_per_min"] = (len(fires) - 1) * 60000.0 / (fires[-1] - fires[0])
    if len(battery) > 1 and battery[-1][0] > battery[0][0]:
        hours = (battery[-1][0] - battery[0][0]) / 3600000.0
        stats["battery_per_hour"] = (battery[0][1] - battery[-1][1]) / hours
    return stats


def report(run, stats, max_drift):
    problems = []
    if stats["drift_max"] is not None and stats["drift_max"] > max_drift:
        problems.append("max drift %d ms exceeds %d ms" % (stats["drift_max"], max_drift))
    if stats["unexplained"]:
        problems.append("%d unexplained skip(s): SKIP rows with no skip_reason" % stats["unexplained"])
    who = ", ".join(v for v in (run.meta.get("model"), run.meta.get("app_version")) if v)
    out = ["run %s%s" % (run.run_id, " (%s)" % who if who else "")]
    out.append("  shots: %d fired, %d completed" % (stats["fired"], stats["completed"]))
    out.append("  drift ms: max %s  mean %s  p95 %s" % (fmt(stats["drift_max"]), fmt(stats["drift_mean"]),
                                                       fmt(stats["drift_p95"])))
    if stats["skips"]:
        parts = ", ".join("%s=%d" % kv for kv in sorted(stats["skips"].items()))
        out.append("  skips: %d (%s)" % (sum(stats["skips"].values()), parts))
    else:
        out.append("  skips: 0")
    out.append("  shots/min: %s" % fmt(stats["shots_per_min"]))
    out.append("  capture ms p95: %s" % fmt(stats["capture_p95"]))
    if stats["battery_first"] is None:
        out.append("  battery: n/a")
    else:
        out.append("  battery: %d%% -> %d%%  (%s %%/h)" % (stats["battery_first"], stats["battery_last"],
                                                         fmt(stats["battery_per_hour"])))
    for p in problems:
        out.append("  FAIL: " + p)
    return out, problems


def main(argv=None):
    ap = argparse.ArgumentParser(description="Summarise a TimeLapse+ LOG.CSV per run.")
    ap.add_argument("csv_file", help="path to TIMELPS/LOG.CSV")
    ap.add_argument("--max-drift-ms", type=int, default=100, help="fail above this FIRE drift (default 100)")
    args = ap.parse_args(argv)
    try:
        with open(args.csv_file, newline="", encoding="utf-8") as f:
            runs = read_runs(f)
    except (OSError, ValueError, UnicodeDecodeError) as e:
        print("analyze_log: %s" % e, file=sys.stderr)
        return 2
    if not runs:
        print("analyze_log: no runs in %s" % args.csv_file, file=sys.stderr)
        return 2
    failed = False
    for run in runs:
        lines, problems = report(run, analyze(run), args.max_drift_ms)
        print("\n".join(lines))
        failed = failed or bool(problems)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
