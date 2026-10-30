# Camera test checklist

One consolidated camera session covers every pending `hw:required` item for issues #2, #4, #5, #7, #8, #9, #10, #11, #12, #13, #14, #15 and #20, using the build from merged `main`. Do **CAPS capture first**: its files answer open questions in several later rows. Paste results and attach the card files on each issue named in the row.

## Setup (both bodies)

- Camera in M mode, long-exposure NR **on** only where a row says so, fresh or fully charged battery (dummy battery for soaks), card with at least 8 GB free.
- Install: `pmca-console install --apk app-debug.apk`, or `adb install -r` over Tweak Wi-Fi ADB. Note the firmware version (MENU, Setup, Version).
- Copy `TIMELPS/` off the card after each part. Before every part, delete or rename the old `LOG.CSV` so each part is easy to find (rows are grouped by `run_id` anyway).

## Part 0: CAPS capture (do first, once per body)

| # | Step | Expect | a6500 | NEX-5T | Issue |
|---|---|---|---|---|---|
| 0.1 | Install, launch the app | Settings screen, no reboot | | | #2 |
| 0.2 | Settings, **Camera info**; let the probe finish | `TIMELPS/CAPS-<model>.txt` written; if the camera crashes, copy the file anyway (last line is the culprit call) | | | #9 |
| 0.3 | At *Turn every dial and press every button*: one notch each way on the control wheel and every dial, step the mode dial, press every button incl. half and full shutter; hold MENU 2 s | Events appear in the key log; file kept | | | #9, #11 |
| 0.4 | Attach `CAPS-ILCE-6500.txt` and `CAPS-NEX-5T.txt` to #9; summarize in `docs/cameras.md` | Both files posted | | | #9, #8 |
| 0.5 | Read CAPS: does `getSupportedExposureBracketPeriods` report int tenths of EV? Does the NEX-5T key log show wheel events at all (`DIAL_UNRELIABLE`)? | Answers noted on #10 and #11 | | | #10, #11 |
| 0.6 | Read CAPS for bulb (a6500): is there a distinct bulb step after `incrementShutterSpeed`? Run the bulb probe if built | Decision gate for #20 | | | #20 |
| 0.7 | CAPS: `incrementShutterSpeed` / `decrementShutterSpeed` direction, and whether any shutter value reads off-table | Noted on #15 | | | #15 |

## ILCE-6500 (a6500)

| # | Step | Expect | Pass | Issue |
|---|---|---|---|---|
| A1 | Launch; open the settings screen | SS, AEL, BRC3, holy grail and MF shown; summary of shown options and `Capabilities:` lines from `LOG.TXT` pasted | | #8 |
| A2 | 10-shot run at 3 s | Run completes; `LOG.TXT` attached | | #2 |
| A3 | Change every setting with the wheel and dials, both directions, and with the arrow keys; hold left or right on a bar | Value moves one table entry per notch; hold repeats about 6 per second; no crash | | #11, #5 |
| A4 | SS on, 100 shots @ 2 s, 1/250 s | 100 files; EXIF times show no bursts; `LOG.TXT` shows no unexplained skips; SS works | | #7, #8 |
| A5 | DOFF on, 20 shots @ 5 s | No black flash between shots; the display wakes on a key | | #5 |
| A6 | BRC3: camera set to 3-frame bracket at 0.7 EV, 10 shots @ 5 s | 30 files, EXIF exposure spread 0.7 EV, 3 frames counted per slot; drive mode restored after the run | | #10 |
| A7 | Start delay: Start = Delay 2 min | Countdown shows; first frame within 1 s of zero | | #12 |
| A8 | Start = At time, 2 min ahead; press a key during the countdown with DOFF on | First frame at the set time; key wakes the screen, then DOFF returns | | #12 |
| A9 | LENR on, 2 s shutter, 3 s interval, "Turn LENR off during runs" ticked | LENR turned off for the run, every slot fires, LENR restored after the run; with the box unticked the warning shows and the interval is raised to 5 s | | #13 |
| A10 | HUD: 30-shot run; press MENU mid-run | HUD fields readable at 640x480; MENU stops within one interval, summary shown, back to settings; free-card estimate sane | | #14 |
| A11 | Any key during a DOFF run | Display wakes without stopping the run | | #14 |
| A12 | Power switch off during a run | Camera powers down cleanly; last file intact | | #14 |
| A13 | Last-shot review set to 2 s | Review shown; shots not skipped at a 5 s interval | | #14 |
| A14 | Holy grail, 30-shot run at dusk or with a changing light source | SS then ISO ramp in EXIF and in the `ss` / `iso` columns of `LOG.CSV`; smooth, at most 1/3 EV per shot | | #4, #15 |
| A15 | Relaunch the app | Previous settings restored | | #5 |
| A16 | Bulb (only if 0.6 found a bulb step): the 5-step plan in `docs/cameras.md` § Bulb exposures, 10 x 60 s frames | Frames not cut at 30 s; results posted | | #20 |

## NEX-5T

| # | Step | Expect | Pass | Issue |
|---|---|---|---|---|
| N1 | Launch; open the settings screen | **SS hidden**; every visible option works; `Capabilities:` lines pasted. Note which of AEL, BRC3, holy grail, MF and burst are shown | | #8 |
| N2 | 10-shot run at 3 s | Run completes; `LOG.TXT` attached | | #2 |
| N3 | Every setting with the arrow keys; note whether the wheel moves anything; hold left or right on a bar | Arrow keys always work; hold repeats; wheel result recorded | | #11, #5 |
| N4 | 100 shots @ 2 s, 1/250 s | 100 files; no bursts in EXIF times; no unexplained skips in `LOG.TXT` | | #7 |
| N5 | BRC3 (if shown): 10 shots @ 0.7 EV | 30 files, 0.7 EV spread; drive mode restored | | #10 |
| N6 | Start delay 2 min | First frame within 1 s of zero | | #12 |
| N7 | LENR: if the body can't set it, long shutter with LENR on | Warning shows and the interval is raised to `2 x shutter + 1 s` | | #13 |
| N8 | HUD run, MENU stop, key-wake under DOFF, power switch off | Same expectations as A10 to A12 | | #14 |
| N9 | Holy grail off | App launches and runs normally | | #4 |
| N10 | Holy grail on, if shown | Runs without `NoSuchMethodError` or reboot; ramp visible, else option hidden | | #4, #15 |
| N11 | Relaunch | Previous settings restored | | #5 |

## Collect and verify

- Copy `TIMELPS/` (`LOG.TXT`, `LOG.CSV`, `CAPS-*.txt`) and a sample of the JPEGs.
- Paste the `Capabilities:` lines and any `SafeCamera:` lines from `LOG.TXT`.
- Run `python3 -I tools/analyze_log.py TIMELPS/LOG.CSV` and paste the summary. Exit 1 means drift above `--max-drift-ms` (default 100) or a SKIP row with no reason. Rows are grouped by `run_id`.
- Run `tools/assemble.sh` on one run's frames (`--run <run_id>`) to confirm the frame match and that the MP4 plays.
- Tick the boxes on #2, #4, #5, #7 to #15 and #20 from the evidence only (a file or photo), then update `docs/cameras.md` with the CAPS results.

## Release gate

Both come after the session above and before tagging 2.0.0.

- **T15 soak (#15)**: a6500 8 h @ 10 s, DOFF on, dummy battery; NEX-5T 4 h @ 10 s, DOFF on. Pass: `analyze_log.py` reports max drift under 100 ms and 0 unexplained skips, the camera responds afterwards with no black screen, and a `dumpsys meminfo` log shows no monotonic heap growth. Auto power-off must not trigger.
- **T18 fresh install (release)**: install the signed release APK through PMCA-RE RELOADED from the project source on a clean camera, on both bodies; the app launches and a short run completes.
