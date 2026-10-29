# Camera test checklist

Run this at the end of every wave on **both** bodies, using the build from merged `main`. Paste the results and attach the card files on each open `hw:required` issue.

## Setup

- Camera in M mode. Use a fresh or fully charged battery, or a dummy battery for soaks. Card has ≥ 8 GB free.
- **Long-exposure NR off** unless the test says otherwise.
- Install: `pmca-console install --apk TimeLapsePlus-debug.apk`, or `adb install -r` over Tweak Wi-Fi ADB.
- Note the firmware version: MENU → Setup → Version.

## Smoke (every wave)

| # | Step | Expect | a6500 | NEX-5T |
|---|---|---|---|---|
| 1 | Launch app from the app menu | Settings screen; no reboot | | |
| 2 | Change every setting with the **wheel/dial** and with the arrow keys | Value moves; no crash | | |
| 3 | 100 shots @ 2 s, SS on (a6500) / off (NEX-5T), 1/250 s | 100 files; log shows no unexplained skips | | |
| 4 | 20 shots @ 5 s with BRC3 (camera set to 3-frame bracket) | 60 files, correct EV spread | | |
| 5 | MENU during a run | Stops within one interval; returns to settings | | |
| 6 | Power switch off during a run | Camera powers down cleanly; last file intact | | |
| 7 | Relaunch | Previous settings restored | | |

## Collect

- Copy `TIMELPS/` off the card (LOG.TXT, plus LOG.CSV from T16 on and CAPS-*.txt from T08).
- From T16 on, run `python3 tools/analyze_log.py TIMELPS/LOG.CSV` and paste its summary.

## Soak (T15 / release only)

- a6500: 8 h @ 10 s, DOFF on, dummy battery.
- NEX-5T: 4 h @ 10 s, DOFF on.
- Pass: analyzer reports max drift < 100 ms, 0 unexplained skips, and the camera responds afterwards with no black screen.
