# TimeLapse+

An in-camera time-lapse (intervalometer) app for Sony cameras that run PlayMemories Camera Apps, built on the [OpenMemories: Framework](https://github.com/ma1co/OpenMemories-Framework).

This is a maintained continuation of [jonasjuffinger/TimeLapse](https://github.com/jonasjuffinger/TimeLapse) (dormant since 2020), consolidating the holy-grail exposure ramping from [Frosbe/SonyHolyGrailTimelapse](https://github.com/Frosbe/SonyHolyGrailTimelapse) and targeting the bodies upstream never tested:

| Camera | PMCA API | Status |
|---|---|---|
| ILCE-6500 (a6500) | 2.17 | primary target |
| NEX-5T | 1.1 | primary target |
| ILCE-6300 (a6300) | 2.15 | upstream's test body |
| other PMCA cameras | — | may work; reports welcome |

Sony closed the PlayMemories store in 2025, so its own Time-lapse app can no longer be downloaded. This app is free and MIT-licensed.

> **Status:** under active rework toward 2.0.0 — see [docs/plan.md](docs/plan.md). Nothing below has been verified on a camera yet; see [Camera compatibility](#camera-compatibility).

## Installation

Install the APK from the [releases](https://github.com/nathanrcast/pmca-timelapse/releases) page with [PMCA-RE RELOADED](https://github.com/masterflai/pmca-re-reloaded) (`pmca-console install --apk <file>`), or the original [Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE).

The package id (`io.github.nathanrcast.timelapse`) differs from upstream's, so both can be installed side by side.

## Usage

The app has no controls for aperture or picture quality. Set the mode (M recommended), shutter speed, ISO, aperture, white balance and picture quality on the camera before starting the app; it uses them. Turn long-exposure noise reduction off (see [Troubleshooting](#troubleshooting)) unless the app offers to do it for you.

### Settings

- **Start** — *Now*, *Delay* (minutes) or *At time* (hh:mm). At time means the next occurrence on the camera clock, so a time that has passed today rolls to tomorrow. The countdown is shown, the camera stays awake during it, and the first frame is timed from the start moment, not from the button press.
- **Interval** — `0` (burst), 0.5 s, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 45 s, then 1, 1.5, 2, 3, 5, 10, 15, 20, 30 and 40 min. Values are fixed steps, not a free slider. Shots are timed from the start moment (`t0 + n x interval`), so a slow frame is skipped and counted, never caught up with a burst, and the cadence does not drift.
- **Shots** — 1, 2, 3, 5, 10 to 100 in tens, 100 to 1000 in 50s, 1000 to 10000 in 500s, then infinite (runs until stopped). In burst mode this is the burst length in seconds.
- **Delay** (Start = Delay) — 0, 1 to 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720 or 1440 min.
- **fps** — 24, 25, 30, 50, 60, 100 or 120; only used to show the clip length for the planned shoot. The app produces no video; see [Getting the video](#getting-the-video).
- The screen shows the shoot duration, end time and clip length as you change values.

### Changing values

Every setting responds to the control wheel and to the front/rear dials where the body has them: clockwise is the next value, counter-clockwise the previous one. Up/down move between settings and left/right step a focused bar, on every body, so nothing depends on a dial working. Holding left/right repeats about six steps a second. On the NEX-5T the wheel may not report events; use the arrow keys.

### Options

An option the body can't support is hidden (your saved choice is kept).

- **SS (silent shutter)** — silent shutter mode, where the body has one. Not on NEX bodies.
- **MF (manual focus)** — switches to manual focus for the run and restores your focus mode afterwards. Focus before you start.
- **AEL (auto exposure lock)** — locks exposure to that of the first shot.
- **BRC3 (3-frame bracketing)** — shoots a 3-frame bracket per interval. The EV step is the one set on the camera, and the app detects a camera already in 3-picture bracket drive even when the box is off. The interval must cover all three frames plus write time; the app warns when it doesn't. Each bracket counts as one shot.
- **Holy grail** — ramps shutter speed, then ISO, to follow changing light (sunsets and sunrises), at most about 1/3 EV per shot. Choose the target exposure (or "Use current exposure"), the slowest shutter and highest ISO allowed, the cooldown and the averaging. Shutter is capped below the interval. Off by default.
- **DOFF (display off)** — turns the display off between shots to save battery. Any key wakes it for a few seconds without stopping the run.
- **Turn LENR off during runs** — when long-exposure noise reduction is on and the shutter is 1 s or longer, the app turns it off for the run and restores it afterwards. Where the body can't set it, a warning shows and the minimum interval is raised to `2 x shutter + 1 s`.
- **Last-shot review** — shows each captured frame for 0 to 5 s (default off). Review costs time on slow bodies, so keep it short.
- **Burst** — at interval `0` the camera shoots as fast as the card allows, for the number of seconds in Shots.

### While shooting

The HUD shows shots done / target, skips, time to the next shot, elapsed and remaining time, battery and the free card space as an estimate of shots left. **MENU stops the run** (within one interval), shows a summary and returns to the settings screen. Other keys only wake the display. Turning the power switch off shuts the camera down normally. A finished run returns to the settings screen after a short summary.

### Camera info

The **Camera info** button (bottom bar, next to Start) writes `TIMELPS/CAPS-<model>.txt` to the card: the camera's model, firmware, which Sony API calls exist, supported value lists, and a log of the keys, dials and wheel you press. It changes no camera setting. Run it once per body and attach the file to bug reports and compatibility reports; steps are in [docs/cameras.md](docs/cameras.md).

## Getting the video

The camera can't encode video (API 10 has no encoder), so assemble the frames on a computer.

1. Power off, take the card out and copy it (or at least `DCIM/` and `TIMELPS/`) to a folder.
2. Install [ffmpeg](https://ffmpeg.org/) and run:

   ```
   tools/assemble.sh <dir> [--fps 24] [--deflicker] [--crop 16:9] [--out file.mp4] [--run <run_id>] [--dry-run]
   ```

   `<dir>` is the `DCIM` folder or a copy; every `.JPG` under it is used, sorted by name. The output is H.264, `yuv420p`, CRF 18. `--deflicker` adds ffmpeg's `deflicker` filter (useful with holy grail or aperture flicker), `--crop 16:9` crops the 3:2 frames centrally, and `--dry-run` prints the ffmpeg command and frame count without running anything.
3. If the card holds several shoots, pass `--run <run_id>`. The script finds `LOG.CSV` in `<dir>` or `<dir>/../TIMELPS/` and keeps only the frames whose file time matches that run's shots (within 2 s, via the log's `uptime_to_wall_ms`). Run ids are listed in `LOG.CSV` (`# run_id=` lines). If the card clock and the computer's time zone disagree, call `tools/frames_for_run.py` directly with `--offset-s` to check the match.
4. Check the shoot itself with `python3 -I tools/analyze_log.py TIMELPS/LOG.CSV`. It reports per run the drift against the schedule, skipped slots with their reasons, and battery use; it exits 1 on drift above `--max-drift-ms` (default 100) or on skips with no reason.

## Troubleshooting

- **Every other frame is missing** — long-exposure noise reduction (LENR) makes the camera shoot a dark frame after each exposure of about 1 s or longer. Turn LENR off in the camera menu or leave "Turn LENR off during runs" ticked, or use an interval above `2 x shutter + 1 s`. The skip reason in `LOG.CSV` is `BUSY`.
- **Shots fail or the first frame never fires** — if single-shot drive mode fails on your body, set continuous drive on the camera and try again. With BRC3 the camera must be able to bracket; check the drive mode on the camera.
- **Silent shutter does nothing or the app errors** — silent shutter doesn't exist on every body (none of the NEX family). Leave SS off; if it is shown and fails, report the body with its CAPS file.
- **Frames are skipped or late with a short interval** — the card is too slow (write time plus RAW+JPEG is longer than the interval). Use a faster card, JPEG only, a lower picture size, or a longer interval. Burst needs a fast card.
- **Camera switches off during a long run** — the app disables auto power-off while it runs, but if the camera still powers down, set Auto Power Off to a long time in the camera menu and use a dummy battery or a full charge for long shoots. Report the length of run and the end of `TIMELPS/LOG.TXT`.
- **Anything else** — attach `TIMELPS/LOG.TXT`, `TIMELPS/LOG.CSV` and the CAPS file to an issue.

## Camera compatibility

Nothing here has been verified on a camera. Expectations come from the Sony API probe in [docs/cameras.md](docs/cameras.md) and are updated after the maintainer's camera session. "Expected" means the app should offer it; options a body lacks are hidden automatically.

| Feature | ILCE-6500 (API 2.17) | NEX-5T (API 1.1) | Other PMCA bodies |
|---|---|---|---|
| Launch and basic interval shooting | expected, unverified | expected, unverified | unknown |
| Start delay and at-time start | expected, unverified | expected, unverified | unknown |
| Dial and wheel input | wheel, front and rear dials: expected, unverified | wheel unreliable; arrow keys expected, unverified | unknown |
| Silent shutter (SS) | expected, unverified | hidden (none on NEX) | decided by the probe |
| Manual focus (MF) | toggle: expected, unverified | probe decides, falls back to focus mode | decided by the probe |
| AEL | expected, unverified | probe decides | decided by the probe |
| BRC3 with EV step and auto-detect | expected, unverified | probe decides | decided by the probe |
| Holy grail | expected, unverified | probe decides | decided by the probe |
| LENR turned off for the run | expected, unverified | probe decides | decided by the probe |
| Burst | expected, unverified | probe decides | decided by the probe |
| Long runs (hours) | expected, unverified | expected, unverified | unknown |
| Bulb over 30 s | not implemented ([research](docs/cameras.md#bulb-exposures-t19-finding)) | not implemented | — |

## Building

Needs a JDK 17–21 and the Android SDK with platform 34 (`sdk.dir` in `local.properties`). `./gradlew :app:assembleDebug` writes `app/build/outputs/apk/debug/app-debug.apk`. Full commands: [AGENTS.md](AGENTS.md) § Commands.

## Credits

- [ma1co](https://github.com/ma1co) — [OpenMemories-Framework](https://github.com/ma1co/OpenMemories-Framework) (MIT; vendored at commit `f8df350` in [app/libs/](app/libs/README.md)), Sony-PMCA-RE, OpenMemories-Tweak
- [obs1dium](https://github.com/obs1dium) — FocusBracket, the original code base
- [jonasjuffinger](https://github.com/jonasjuffinger) — TimeLapse
- [Frosbe](https://github.com/Frosbe) — holy-grail ramping
- [masterflai](https://github.com/masterflai) — PMCA-RE RELOADED

Not affiliated with or endorsed by Sony. Installing third-party apps on your camera is at your own risk.

## License

MIT — see [LICENSE.txt](LICENSE.txt).
