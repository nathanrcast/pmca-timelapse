# Camera test checklist

Run this at the end of every wave on **both** bodies, using the build from merged `main`. Paste the results and attach the card files on each open `hw:required` issue.

## Setup

- Camera in M mode. Use a fresh or fully charged battery, or a dummy battery for soaks. Card has ≥ 8 GB free.
- **Long-exposure NR off** unless the test says otherwise.
- Install: `pmca-console install --apk app-debug.apk`, or `adb install -r` over Tweak Wi-Fi ADB.
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

- Copy `TIMELPS/` off the card (LOG.TXT for errors, LOG.CSV with one row per shot from T16 on, and CAPS-*.txt from T08).
- Once per body, and again after a firmware update: Settings → **Camera info**, turn every dial and press every button, hold MENU 2 s, then attach `TIMELPS/CAPS-<model>.txt` to the T08 issue (`docs/cameras.md`).
- From T07 on, paste the `Capabilities:` lines and any `SafeCamera:` lines from `TIMELPS/LOG.TXT`. Note which of SS, MF, AEL, BRC3 and holy grail the settings screen shows.
- From T16 on, run `python3 -I tools/analyze_log.py TIMELPS/LOG.CSV` and paste its summary (exit 1 means drift above `--max-drift-ms`, default 100, or a SKIP row with no reason). Rows are grouped by `run_id`; times are uptime ms and the `# uptime_to_wall_ms` header line converts them to wall clock.

## Soak (T15 / release only)

- a6500: 8 h @ 10 s, DOFF on, dummy battery.
- NEX-5T: 4 h @ 10 s, DOFF on.
- Pass: analyzer reports max drift < 100 ms, 0 unexplained skips, and the camera responds afterwards with no black screen.

Memory, over Tweak Wi-Fi ADB (adb on `$ADB` or PATH; the camera needs Tweak's ADB on and the host on the same LAN):

1. Start the run on the camera, then on the host: `tools/soak_meminfo.sh <camera-ip> 30`. It runs `adb connect` and `dumpsys meminfo io.github.nathanrcast.timelapse` every 30 min and appends timestamped output to `soak-meminfo.log` (`--dry-run` prints the commands; Ctrl-C stops it; `SOAK_LOG` moves the file).
2. After the run: `python3 -I tools/meminfo_trend.py soak-meminfo.log`. It prints native and dalvik heap growth with kB/h and exits 1 if a heap never shrank and grew by more than `--threshold-kb` (default 2048), 2 if the log has too few samples. Attach the output and a log excerpt to the T15 issue.
3. Power-off: after the run, the camera must not have powered off by itself. With DOFF on, press any button after the run: the panel must come back.
4. DOFF recovery: during a DOFF run, kill the app (`adb shell am force-stop io.github.nathanrcast.timelapse`) and relaunch it; the panel must come back on at the settings screen.
