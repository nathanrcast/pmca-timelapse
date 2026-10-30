# Camera bodies

What each supported body's Sony API actually offers, from its CAPS file (T08), and how the app decides which options to show (T07). The per-body sections summarize each CAPS file; T10/T19 read their key codes and long-exposure findings from it.

## How to capture CAPS files

1. Install the current build and launch TimeLapse+.
2. On the settings screen, select **Camera info** in the bottom bar (between Close and Start).
3. The app opens the camera, reads everything without changing any setting, releases the camera and writes `TIMELPS/CAPS-<model>.txt` to the card as it goes, showing the current section and the path on screen. If the card rejects the long name, the file is `TIMELPS/CAPS.TXT`. Each run overwrites the file.
4. When the screen reads *Turn every dial and press every button (DELETE last)*: turn the control wheel and every dial one notch each way, step the mode dial through every position, and press every button (half and full shutter included; nothing is acted on). Each event is appended to the file and the last 15 show on screen.
5. To exit, hold MENU for 2 s and release it, or press DELETE (Back on a body without one). A short MENU press is only logged. If the screen says *Key log NOT being saved*, the file couldn't be written: check the card.
6. Copy `TIMELPS/CAPS-*.txt` off the card and attach it to the T08 issue.

If the camera crashes or reboots during the probe, still copy the file. The name of each call is written before the call is made, so the last line, a call name with no ` = ` or ` !! `, is the call that crashed it. Groups that finished are synced to the card.

A CAPS file has these sections:

- **Device**: `DeviceInfo` (model, firmware, PMCA API version) and `android.os.Build`. The serial number is omitted.
- **Used methods**: every Sony method the app calls today, each marked `present` or `ABSENT` on this firmware.
- **Classes**, **Methods**, **Constants**: every public method signature of `CameraEx`, `CameraEx$ParametersModifier`, the `CameraEx` listener and callback interfaces, `CameraSequence` (if present) and the other Sony classes the app touches, plus the runtime values of the `CameraEx` and `ParametersModifier` constants.
- **ScalarInput ISV_\* constants**: the firmware's key-code table.
- **Values**: the result of every no-arg `get*`/`is*` on `ParametersModifier` and `Camera.Parameters`, one per line as `name = value` or `name !! <exception>`, plus `Camera.Parameters.flatten()` of the normal and the `getSupportedParameters()` parameters. Control characters in values are escaped (`\n`, `\r`, `\t`, `\uXXXX`).
- **Key log**: seconds since start, `down`/`up`/`repeat`, keyCode, scanCode, the `ScalarInput` constant name, and the dial position for dial events.

## How the app decides what a body can do (T07)

`camera/SafeCamera` decides in four steps:

1. **Methods, once per process.** Every Sony method the app calls (`core.camera.SonyMethod`; each one is also in a CAPS file's *Used methods* section) is looked up by reflection. A method is absent if its class, a parameter type or the method itself can't be resolved. Nothing absent is ever called.
2. **Values, at every run start.** Each supported-value query (`core.camera.Query`) present on the firmware is called on a `ParametersModifier`. A query that is absent or throws stays *unknown*. The values can depend on the body's current mode, so they are read again each time the shoot screen opens the camera, and the run is masked against those values. The settings screen also reads them once, on a background thread, to decide what to show. If it can't open the camera, it tries once more on its next resume, and shows every option until then.
3. **Rules.** Each feature (`core.camera.Feature`) needs all of its methods. Where the firmware reports a value list, the value the app sets must be in it; an unknown list doesn't veto. The two `isSupported*` answers must be known and `true`.
4. **Profile.** `camera/ModelProfile` then switches features off for behaviour reflection can't see, keyed on `DeviceInfo.getModel()`. A profile can't switch a feature on.

An option the body lacks is hidden on the settings screen, but its saved value is kept, so a probe that failed once doesn't lose the user's choice. The run's copy of the settings is masked instead (`core.camera.RunOptions`, the single gate), and the masked options are logged. If a *core* method (open, release, `getNormalCamera`, `createParametersModifier`, `setShutterListener`, `burstableTakePicture`, `cancelTakePicture`) is missing, the shoot screen shows *Camera unavailable* instead of opening the camera. Each probe and each run start writes the result to `TIMELPS/LOG.TXT` as `Capabilities:` lines (model and profile, absent methods, features on/off, every value), and each failing camera call is logged once per run as `SafeCamera: UNSUPPORTED …` or `SafeCamera: FAILED …`.

| Option | Feature | Needs these methods | and these values (when reported) |
|---|---|---|---|
| SS | `SILENT_SHUTTER` | `setSilentShutterMode`, `isSupportedSilentShutterMode` | `isSupportedSilentShutterMode()` is `true` |
| AEL | `AE_LOCK` | `setAutoExposureLock` | `getSupportedAutoExposureLocks` has `locked` |
| BRC3 | `BRACKET_3` | `setDriveMode`, `setBracketMode`, `setExposureBracketMode`, `setExposureBracketPeriod`, `setNumOfBracketPicture` | drive modes have `bracket`, bracket modes `exposure`, exposure-bracket modes `single`, bracket counts `3`. Read at open, before the run changes anything: `getDriveMode`, `getNumOfBracketPicture`, `getExposureBracketPeriod` (tenths of EV), `getSupportedExposureBracketPeriods`. The run brackets when the box is on or the body is already in 3-picture bracket drive; the EV step is the body's own (0.7 EV only if unreadable); a count other than 3 runs single frames |
| Holy grail | `HOLY_GRAIL` | `setProgramLineRangeOverListener`, `getShutterSpeed`, `getISOSensitivity`, `setISOSensitivity`, `incrementShutterSpeed`, `decrementShutterSpeed`, `createEmptyParameters` | — |
| MF | `TOGGLE_FOCUS`, else `FOCUS_MODE_MANUAL` | `setToggleFocusMode`, `isSupportedToggleFocusMode`; the fallback needs none (Android `setFocusMode`) | `isSupportedToggleFocusMode()` is `true`; for the fallback, `getSupportedFocusModes` has `manual` |
| Burst (interval 0) | `BURST` | `setDriveMode`, `getSupportedBurstDriveSpeeds`, `setBurstDriveSpeed`, `setBurstDriveButtonReleaseBehave` | drive modes have `burst`, burst speeds not empty; otherwise the settings screen shows the burst position as *n/a* and the run uses the shortest interval (1 s) |

MF prefers the AF/MF toggle. The stubs have no getter for the toggle state, so the app reads the focus mode first: if the body is already in manual focus it leaves the toggle alone, otherwise it engages the toggle and releases it when the run ends. While the app holds the toggle, a flag in its preferences says so; a run that never ended cleanly (crash, reboot) leaves the flag set, and the next run start releases the toggle before anything else. The toggle is engaged only after the run's parameters applied. If the toggle call throws, it falls back to `setFocusMode("manual")` and restores the previous focus mode afterwards. `isSupportedLongExposureNR` and `getSupportedISOSensitivities` are read and logged for T12 and T14 but gate nothing yet. There is no shutter-speed list API in the stubs; the holy-grail ramp uses `Util.SHUTTER_SPEEDS`.

## ILCE-6500

PMCA API 2.17. **CAPS file: pending** (T08); replace the expectations below with its *Used methods* and *Values* results.

Expected (unverified):

- Profile: none.
- Every method in the *Used methods* list present.
- SS, AEL, BRC3, holy grail and MF shown; MF through the toggle.

CAPS results: pending.

## NEX-5T

PMCA API 1.1. **CAPS file: pending** (T08); replace the expectations below with its *Used methods* and *Values* results.

Expected (unverified):

- Profile `NEX`, which applies to every model starting with `NEX-`. It turns SS off: no NEX body has a silent shutter, and upstream reports it missing on the NEX-5R/5T ([`up#5`](https://github.com/jonasjuffinger/TimeLapse/issues/5)). It also sets the quirk `DIAL_UNRELIABLE` for T10. The quirk only means input must also work with the arrow keys, so it's safe for the whole family.
- SS hidden even if the probe finds the methods.
- AEL, BRC3, holy grail, toggle focus and burst: the probe decides. Any of them may be missing on API 1.1. If they are, the option is hidden, and MF falls back to the focus mode.

CAPS results: pending.

## Bulb exposures (T19 finding)

**Verdict: feasible-unverified.** The stubs have everything a bulb run needs except a documented "bulb" value, and one prior-art report says an app can set bulb and shoot it. Nothing was tested on a camera (no CAPS file yet), so the sentinel value and the stop call are unknown. The upstream maintainer's answer, "not possible from the app" ([`up#20`](https://github.com/jonasjuffinger/TimeLapse/issues/20)), predates the evidence below and was not backed by an API check.

### What the stubs offer (`openmemories-stubs-f8df350`)

There is **no BULB constant** anywhere in `CameraEx`, `ParametersModifier`, `ShutterSpeedInfo` or `CameraSequence`, and no `setShutterSpeed` or supported-shutter-speed list. Shutter speed is only read (as a `Pair` of numerator, denominator) and stepped.

Shutter speed:
- `CameraEx$ParametersModifier`: `android.util.Pair getShutterSpeed()`. Reads the current value; what it returns in bulb is unknown (the probe must log it).
- `CameraEx`: `void adjustShutterSpeed(int)`, `void incrementShutterSpeed()`, `void decrementShutterSpeed()`. This is how a value is set; bulb sits past 30" in the step order on bodies that have it.
- `CameraEx`: `void setShutterSpeedChangeListener(CameraEx$ShutterSpeedChangeListener)`, with `onShutterSpeedChange(CameraEx$ShutterSpeedInfo, CameraEx)`. `ShutterSpeedInfo` has public int fields `currentShutterSpeed_n/_d`, `currentAvailableMin_n/_d`, `currentAvailableMax_n/_d`. The bulb step shows up here (a sentinel pair or the max), which is the cheapest way to detect it.

Starting and stopping an exposure:
- `CameraEx`: `void burstableTakePicture()` (what the app uses today), `void cancelTakePicture()`, `void cancelExposure()`.
- `CameraEx`: `void startDirectShutter()`, `void stopDirectShutter(CameraEx$DirectShutterStoppedCallback)` with `onShutterStopped(CameraEx)`; `ParametersModifier.isDirectShutterSupported()` says whether the body has it. Direct shutter is the one start/stop pair in the API. Its only public use found is immediate capture (LegacyLenses calls `startDirectShutter()` once after `open`, then shoots normally), so whether it holds the shutter open is open.
- `CameraEx`: `void startSelfTimerShutter()`, `boolean cancelSelfTimerShutter()`; `ParametersModifier`: `setSelfTimer(int)`, `getSupportedSelfTimers()`. Not a bulb path.
- `CameraSequence` (`open(CameraEx)`, `setShutterSequenceCallback`, `setSplitShutterSequenceCallback`, `isSplitExposureSupported()`, `setReleaseLock(boolean)`) hands raw data to the app. It has option keys `SPLIT_EXPOSURE_COUNT_LIMIT` and `SPLIT_EXPOSURE_INTERVAL`, and `SplitExposureProgressInfo` has `int exposureTime`, `String status`, with status constants `CONTINUE`, `DARK`, `END`, `ERROR`, `INTERRUPT`, `SCENE`. This is the firmware's split-exposure machinery (multi-shot stacking), not a bulb timer, and it would make the app process raw data. Ruled out as the primary route; the probe should still record `isSplitExposureSupported()`.

Completion callbacks:
- `CameraEx$ShutterListener`: `onShutter(int status, CameraEx)`, `STATUS_OK = 0`, `STATUS_CANCELED = 1`, `STATUS_ERROR = 2`. The app already uses it (`ShutterBridge`); it is the "shutter cycle ended" event.
- `CameraEx$ExposureCompleteListener`: `onDone(int, CameraEx$ExposureInfo, CameraEx)`, set with `CameraEx.setExposureCompleteListener(...)`. `ExposureInfo` has `ShutterSpeedNumber`, `ShutterSpeedDeanom` (sic), `FNo`, `IsoSpeedRate`. It reports the actual shutter time, so a bulb frame's real length can be logged.
- `CameraEx$StoreImageCompleteListener`: `onDone(int, CameraEx$StoreImageInfo, CameraEx)`, set with `setStoreImageCompleteListener(...)`. `StoreImageInfo` has `DirectoryName`, `FileName`, `FileNo` and the capture date. This fires when the file is written, so it marks the end of readout and long-exposure NR.
- `CameraEx$OnCaptureStatusListener`: `onStart(int, CameraEx)`, `onEnd(int, int, CameraEx)`, `REASON_NORMAL = 1`, `REASON_CANCEL = 2`. Its setter is not in `CameraEx`'s public list, so it is probably reachable another way; the probe should look.
- `CameraEx$JpegListener.onPictureTaken(byte[], CameraEx)` is the Android-style callback and carries no timing we need.

### Prior art

- OpenMemories-Framework issue 24, "How to Bulb shooting?" ([link](https://github.com/ma1co/OpenMemories-Framework/issues/24)): the reporter set M mode, F2.8, ISO 200, stepped the shutter with `adjustShutterSpeed()`, confirmed the bulb step through `onShutterSpeedChange()`, then called `takePicture`. The shutter was forced to 30 s. The reporter later wrote that they "succeeded to shoot bulb mode" and that the cause was `setDriveMode("burst")`. No code was posted and the stop mechanism isn't described. Takeaway: **bulb is reachable by stepping the shutter, but only in single drive mode, and the app's burst drive mode (`burstableTakePicture` with `burst`) clamps it to 30 s.** TimeLapse+ must not use burst drive for a bulb frame.
- OpenMemories-Framework issue 40, "BULB shooting" ([link](https://github.com/ma1co/OpenMemories-Framework/issues/40)): asks whether `startBulbShooting`/`stopBulbShooting` (Camera Remote API names) exist in the framework. Unanswered.
- Sony-PMCA-RE issue 70, "Smart Remote: Bulb Shooting hack" ([link](https://github.com/ma1co/Sony-PMCA-RE/issues/70)): Sony's own Smart Remote app has bulb, gated by a model blacklist; on one blacklisted body (HX60) selecting bulb gave 30 s. So Sony's app does it with the same CameraEx layer, but the bulb capability is firmware- and body-dependent.
- `up#20` ([link](https://github.com/jonasjuffinger/TimeLapse/issues/20)): the upstream maintainer said it isn't possible from the app, without detail. `up#13` is the same request.
- No open-source app found that times a bulb exposure; the PlayMemories "Light Trail" and "Touchless Shutter" apps do it commercially, so the capability exists in the platform. Code search for `startDirectShutter` found only [LegacyLenses](https://github.com/vlousada/LegacyLenses) and the framework's own stubs.

### Why this is not "feasible" yet

1. Which `getShutterSpeed()` pair (or `ShutterSpeedInfo` value) means bulb on the a6500 is unknown, and whether `incrementShutterSpeed` reaches it from 30" in M mode.
2. Whether an exposure started by `burstableTakePicture()` in bulb stays open until `cancelExposure()`/`cancelTakePicture()`, or until the shutter "button" is released (then `startDirectShutter`/`stopDirectShutter` or a key event is the real pair), or never ends by itself without a button.
3. Whether the camera (a6500 on API 2.17) takes drive mode `single` plus bulb under the app's `configure()` at all.

Each of these is answerable by one on-camera session with the probe extension below.

### Design (if the probe confirms it)

**Scheduler.** A bulb frame occupies the interval slot `exposure + readout margin`, where margin covers sensor readout, the store-image write and, if long-exposure NR is on, a dark frame of the same length as the exposure (the `Traps` entry in `AGENTS.md`). So slot = `exposure * (NR ? 2 : 1) + write margin`; the scheduler's minimum interval becomes that value, and `ShotPlan` duration, shots remaining and clip length use it. The wait for completion is event-driven (`StoreImageCompleteListener.onDone`, with a timeout of slot + 10 s), not a fixed sleep. Exposures at or under 30 s keep the current path unchanged.

**`SafeCamera` sketch** (all guarded by `caps.has(...)`; return `Result<...>` like the existing calls):
- `Result<Void> selectBulb()`: sets drive mode `single`, then `incrementShutterSpeed()` until `ShutterSpeedChangeListener` reports the bulb value or `currentAvailableMax` stops changing (bounded loop); fails with `unsupported` if bulb is never reached.
- `Result<Void> startBulb()`: `burstableTakePicture()` (or `startDirectShutter()` if the probe shows that is the open call), and records the start time on the `Clocks.UPTIME` clock.
- `Result<Void> endBulb()`: `cancelExposure()` after N s (alternative per probe: `stopDirectShutter(cb)`). Completion arrives through `ShutterListener`/`StoreImageCompleteListener`.
- `Result<Void> setExposureCompleteListener(...)` and `setStoreImageCompleteListener(...)`, each with its own small bridge class in `camera/` (the Sony-interface rule in `AGENTS.md`).

**`SonyMethod` entries to add** (all optional, `core=false`): `ADJUST_SHUTTER_SPEED` (`CameraEx`, `adjustShutterSpeed`, `int`), `CANCEL_EXPOSURE` (`cancelExposure`), `SET_SHUTTER_SPEED_CHANGE_LISTENER` (`setShutterSpeedChangeListener`, `CameraEx$ShutterSpeedChangeListener`), `SET_EXPOSURE_COMPLETE_LISTENER` (`setExposureCompleteListener`, `CameraEx$ExposureCompleteListener`), `SET_STORE_IMAGE_COMPLETE_LISTENER` (`setStoreImageCompleteListener`, `CameraEx$StoreImageCompleteListener`), `START_DIRECT_SHUTTER` (`startDirectShutter`), `STOP_DIRECT_SHUTTER` (`stopDirectShutter`, `CameraEx$DirectShutterStoppedCallback`), `IS_SUPPORTED_DIRECT_SHUTTER` (`ParametersModifier.isDirectShutterSupported`). A new `Feature.BULB` needs `INCREMENT_SHUTTER_SPEED`, `GET_SHUTTER_SPEED`, `CANCEL_EXPOSURE`, `SET_STORE_IMAGE_COMPLETE_LISTENER` and `SET_DRIVE_MODE`, plus a drive mode list containing `single`. A `ModelProfile` entry keeps it off on bodies where the test fails.

**CapsDump probe** (read-only except the step test): add the methods above to *Used methods*; log `getShutterSpeed()`, `isDirectShutterSupported()` and `CameraSequence.isSplitExposureSupported()` in *Values*; and add an opt-in **Bulb probe** step (a button, never automatic, camera in M mode on a tripod, lens cap on). It registers `ShutterSpeedChangeListener`, `ExposureCompleteListener` and `StoreImageCompleteListener`, steps `incrementShutterSpeed()` up to 40 times logging every `ShutterSpeedInfo` (n/d, min, max) and `getShutterSpeed()`, and stops when the value stops changing. It then writes the pair at that step and the drive mode used. It does not fire the shutter; the shot is the on-camera test below.

**On-camera test plan (a6500, maintainer).** M mode, drive single, ISO 100, lens cap on, long-exposure NR off, then on.
1. Run Camera info with the Bulb probe; post `CAPS-ILCE-6500.txt`. Decision gate: a distinct bulb step exists.
2. One manual 35 s frame via `startBulb()`/`endBulb()` at 35 s: check the Exif exposure time and that the frame is not cut at 30 s.
3. 10 frames at 60 s, interval = slot, NR off: all ten files present, each Exif time 60 s +/- 1 s, no skipped frames, LOG.TXT shows each `onDone` and the measured start-to-store time.
4. The same with NR on: confirm every other slot is a dark frame and the slot formula holds.
5. Stop mid-exposure (back out of the run): the camera returns to a usable state with no stuck shutter, and the next launch starts clean.
Pass when steps 2 and 3 hold on the a6500; the NEX-5T follows only if its probe shows a bulb step.
