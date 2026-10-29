# TimeLapse+ plan

## Why

Sony stopped selling PlayMemories Camera Apps on 2023-09-30 and shut the download service on 2025-08-31, so its Time-lapse app can't be obtained any more. The open-source alternative, [jonasjuffinger/TimeLapse](https://github.com/jonasjuffinger/TimeLapse) (MIT, v1.4.0), was last updated in June 2020 and was only tested on an a6300. This project hard-forks it to:

1. bring together the useful forks,
2. fix the bugs that hit the **a6500** and **NEX-5T**,
3. harden it for long unattended runs.

## Prior art (surveyed 2026-10-29)

| Project | State | Use |
|---|---|---|
| [jonasjuffinger/TimeLapse](https://github.com/jonasjuffinger/TimeLapse) | MIT, last push 2020-06, 22 open issues | Base (history imported) |
| [Frosbe/SonyHolyGrailTimelapse](https://github.com/Frosbe/SonyHolyGrailTimelapse) | Fork, 2023-11, v1.0.0b | Holy-grail exposure ramping → T03 |
| [JEF1056/TimeLapse](https://github.com/JEF1056/TimeLapse) | Fork, 2026-08 | Press-and-hold sliders, delay countdown, display timing → T04 |
| [Zyphoriate/TimeLapse](https://github.com/Zyphoriate/TimeLapse) | Fork, v1.5.0 2026-06 | DOFF review freeze → T04 |
| upstream `testing` branch | 1 commit | Display battery-save experiment → T13/T15 |
| [masterflai/pmca-re-reloaded](https://github.com/masterflai/pmca-re-reloaded) | Active | Installer; `pmca-source.yaml` distribution → T18 |

Upstream bugs this plan targets (links go to the upstream tracker):

- [5](https://github.com/jonasjuffinger/TimeLapse/issues/5): NEX-5R drift → bursts; wheel not read
- [26](https://github.com/jonasjuffinger/TimeLapse/issues/26) / [50](https://github.com/jonasjuffinger/TimeLapse/issues/50): half the shots
- [41](https://github.com/jonasjuffinger/TimeLapse/issues/41): BRC3 count and EV
- [36](https://github.com/jonasjuffinger/TimeLapse/issues/36): start delay
- [39](https://github.com/jonasjuffinger/TimeLapse/issues/39): last-shot review
- [46](https://github.com/jonasjuffinger/TimeLapse/issues/46): large-card jitter
- [49](https://github.com/jonasjuffinger/TimeLapse/issues/49): can't stop / power off
- [52](https://github.com/jonasjuffinger/TimeLapse/issues/52): black screen after 1-day runs
- [4](https://github.com/jonasjuffinger/TimeLapse/issues/4): `NoSuchMethodError` on older bodies
- [8](https://github.com/jonasjuffinger/TimeLapse/issues/8) / [29](https://github.com/jonasjuffinger/TimeLapse/issues/29): slider steps
- [13](https://github.com/jonasjuffinger/TimeLapse/issues/13) / [20](https://github.com/jonasjuffinger/TimeLapse/issues/20): bulb
- [34](https://github.com/jonasjuffinger/TimeLapse/issues/34): LENR

## Platform facts

- Both target bodies run **Android 2.3.7 (API 10, Dalvik)**. Their PMCA API levels differ a lot: NEX-5T is 1.1, a6500 is 2.17. Methods present on the a6500 may be missing on the NEX-5T, so everything is capability-probed (T07).
- Upstream builds with `compileSdk 10`, Java 1.6, AGP 3.5.0 and jcenter, plus `OpenMemories-Framework` `-SNAPSHOT` from jitpack. Modern JDKs can't run that toolchain (T01).
- Input arrives as Sony `ScalarInput` key codes. Dial codes differ per body (T08 → T10).
- Upstream's capture loop schedules each shot from the *previous* shot's time with a −150 ms fudge, so drift accumulates and the loop can fire bursts (T06).
- Dev loop: OpenMemories-Tweak's "Enable ADB" exposes `adb` over Wi-Fi on port 5555 for `install -r` and `logcat`. USB install goes through `pmca-console install --apk`.

## Waves

Each wave is a GitHub milestone, and each ticket is an issue with a spec in `docs/tickets/`. **HW** marks tickets whose acceptance needs the maintainer to run the build on a camera.

| Wave | Ticket | Title | Size | HW | Depends on |
|---|---|---|---|---|---|
| 0 | [T00](tickets/T00-bootstrap.md) | Bootstrap repo | M | | — |
| 1 | [T01](tickets/T01-modern-build.md) | Spike: modern build toolchain | L | ✔ | T00 |
| 1 | [T02](tickets/T02-ci.md) | CI + release script | S | | T01 |
| 1 | [T03](tickets/T03-merge-holygrail.md) | Merge holy-grail fork | M | ✔ | T01 |
| 1 | [T04](tickets/T04-port-2026-fork-fixes.md) | Port 2026 fork fixes | M | ✔ | T03 |
| 1 | [T08](tickets/T08-capsdump.md) | Spike: CapsDump diagnostics | M | ✔ | T01 |
| 2 | [T05](tickets/T05-core-module.md) | Extract `:core` module | M | | T02, T03 |
| 2 | [T06](tickets/T06-scheduler.md) | Drift-free scheduler | M | ✔ | T05 |
| 2 | [T07](tickets/T07-capability-layer.md) | Capability layer + model profiles | L | ✔ | T03, T08 |
| 2 | [T16](tickets/T16-shot-log.md) | Per-shot CSV log + analyzer | S | | T05 |
| 3 | [T09](tickets/T09-brc3.md) | BRC3 count/EV fix | M | ✔ | T06, T07 |
| 3 | [T10](tickets/T10-dial-input.md) | Dial input + stepped values | M | ✔ | T07, T08 |
| 3 | [T11](tickets/T11-start-delay.md) | Start delay / scheduled start | S | ✔ | T04, T06 |
| 3 | [T12](tickets/T12-lenr-guard.md) | LENR guard | S | ✔ | T07 |
| 3 | [T13](tickets/T13-hud-stop.md) | Shooting HUD + reliable stop | M | ✔ | T06, T07 |
| 3 | [T14](tickets/T14-holygrail-core.md) | Holy-grail ramp into `:core` | M | ✔ | T05, T07 |
| 4 | [T15](tickets/T15-soak.md) | Long-run robustness + soak | L | ✔ | wave 3 |
| 4 | [T17](tickets/T17-assemble-docs.md) | Desktop assembly + docs | S | | T16 |
| 4 | [T18](tickets/T18-release.md) | Release 2.0.0 | S | ✔ | T15, T17 |
| 4 | [T19](tickets/T19-bulb-spike.md) | Stretch: bulb > 30 s | M | ✔ | T08 |

Critical path: T00 → T01 → T03 + T08 → T07 / T05 → T06 → wave 3 → T15 → T18.

## Verification

- **Every PR:** CI green. That means a debug APK build plus lint, then `:core` unit tests from T05 on. Scheduler and ramp logic get fake-clock JUnit tests, so they need no hardware.
- **Every wave:** the maintainer runs [hw_test_checklist.md](hw_test_checklist.md) on both bodies and attaches the card logs. `tools/analyze_log.py` (T16) must report:
  - max drift < 100 ms
  - every skip explained in the log
- **Release:** an 8 h soak on the a6500 and a 4 h soak on the NEX-5T both pass, and a fresh install from `pmca-source.yaml` through PMCA-RE RELOADED works on both.
