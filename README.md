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

> **Status:** under active rework — see [docs/plan.md](docs/plan.md). The usage notes below describe the upstream 1.4.0 behavior and will change.

## Installation

Install the APK from the [releases](https://github.com/nathanrcast/pmca-timelapse/releases) page with [PMCA-RE RELOADED](https://github.com/masterflai/pmca-re-reloaded) (`pmca-console install --apk <file>`), or the original [Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE).

The package id (`io.github.nathanrcast.timelapse`) differs from upstream's, so both can be installed side by side.

## Usage

The app has no controls for shutter speed, aperture, ISO or picture quality — set those on the camera before starting the app and it uses them. Set the camera to manual focus if you don't want it to refocus before each shot.

Start the app, set the interval and the number of pictures. The screen shows how long the shoot takes and how long the resulting clip will be at the chosen fps (the app doesn't produce a video). Press start; press MENU to stop.

- **SS (Silent Shutter)** — no effect on cameras without a silent shutter mode.
- **MF (Manual Focus)** — sets focus to manual. Focus before starting.
- **AEL (Auto Exposure Lock)** — locks exposure to that of the first shot.
- **BRC3** — set three-image exposure bracketing on the camera, then tick BRC3. The interval must be long enough for all three frames.
- **DOFF (Display Off)** — turns the display off between shots.
- **Burst mode** — at the lowest interval the camera shoots as fast as the card can write, for the duration set by the second slider.

### Known issues (upstream 1.4.0)

- Silent shutter must be off on some cameras.
- If single-shot drive mode fails, try continuous.
- Turn long-exposure noise reduction off when the interval is less than twice the shutter speed — otherwise every other frame is skipped.

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
