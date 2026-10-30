# T16: Per-shot CSV log + analyzer

**Labels:** type:feature area:tools size:S · **Milestone:** Wave 2 · **Depends on:** T05

## Goal
Make every run self-describing, so that timing and exposure problems can be diagnosed from the card without a camera in hand.

## Design
- `TIMELPS/LOG.CSV`, appended and flushed every 10 rows and on stop. Columns:

  `run_id,slot,scheduled_ms,actual_ms,capture_done_ms,result(FIRE|SKIP|SLOT_DONE),skip_reason,ss,iso,ev,bracket_idx,battery_pct,free_mb`

  A header row carries the model, firmware, app version and settings JSON.
- Keep the existing LOG.TXT for exceptions.
- `tools/analyze_log.py` (Python 3 stdlib only, run with `python3 -I`) prints:
  - per-run drift (actual − scheduled): max, mean and p95
  - skip count by reason
  - shots per minute
  - capture-duration p95
  - battery drop per hour

  It exits non-zero when `--max-drift-ms` (default 100) is exceeded or there are unexplained skips.
- The `:core` side gets a `ShotRecord` and a CSV formatter, both unit-tested.

## Acceptance
- [ ] Unit test for formatter escaping and header.
- [ ] `tools/analyze_log.py tests/fixtures/sample.csv` matches `tests/fixtures/sample.expected.txt` (add the fixture, plus a tiny `tools/test_analyze_log.py`).
- [ ] The writer never blocks the capture thread (writes are handed to a background `HandlerThread`).

## Inherited from T03
- With holy grail on, each shot makes about 7 `log()` calls on the main thread before the shutter fires (`shoot`, `holyGrailPrePhotoEvent`, and the entry logs in `updateShutterSpeed`/`updateISOLevel`). Each call builds a new `SimpleDateFormat` for its timestamp prefix, and `Logger` opens, appends to and closes `TIMELPS/LOG.TXT`. Replace them with one structured CSV row per shot on the background writer, and reuse a single formatter.
