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

`camera/SafeCamera` probes once per app session, the first time the settings screen opens:

1. **Methods.** Every Sony method the app calls (`core.camera.SonyMethod`; each one is also in a CAPS file's *Used methods* section) is looked up by reflection. A method is absent if its class, a parameter type or the method itself can't be resolved. Nothing absent is ever called.
2. **Values.** The camera is opened, each supported-value query (`core.camera.Query`) present on the firmware is called on a `ParametersModifier`, and the camera is released. A query that is absent or throws stays *unknown*. If the camera can't be opened, every value is unknown and the shoot screen probes again when it opens the camera.
3. **Rules.** Each feature (`core.camera.Feature`) needs all of its methods. Where the firmware reports a value list, the value the app sets must be in it; an unknown list doesn't veto. The two `isSupported*` answers must be known and `true`.
4. **Profile.** `camera/ModelProfile` then switches features off for behaviour reflection can't see, keyed on `DeviceInfo.getModel()`. A profile can't switch a feature on.

An option the body lacks is hidden on the settings screen and forced off for the run. If a *core* method (open, release, `getNormalCamera`, `createParametersModifier`, `setShutterListener`, `burstableTakePicture`, `cancelTakePicture`) is missing, the shoot screen shows *Camera unavailable* instead of opening the camera. Each launch writes the result to `TIMELPS/LOG.TXT` as `Capabilities:` lines (model and profile, absent methods, features on/off, every value), and each failing camera call is logged once per run as `SafeCamera: UNSUPPORTED …` or `SafeCamera: FAILED …`.

| Option | Feature | Needs these methods | and these values (when reported) |
|---|---|---|---|
| SS | `SILENT_SHUTTER` | `setSilentShutterMode`, `isSupportedSilentShutterMode` | `isSupportedSilentShutterMode()` is `true` |
| AEL | `AE_LOCK` | `setAutoExposureLock` | `getSupportedAutoExposureLocks` has `locked` |
| BRC3 | `BRACKET_3` | `setDriveMode`, `setBracketMode`, `setExposureBracketMode`, `setExposureBracketPeriod`, `setNumOfBracketPicture` | drive modes have `bracket`, bracket modes `exposure`, exposure-bracket modes `single`, bracket counts `3` |
| Holy grail | `HOLY_GRAIL` | `setProgramLineRangeOverListener`, `getShutterSpeed`, `getISOSensitivity`, `setISOSensitivity`, `incrementShutterSpeed`, `decrementShutterSpeed`, `createEmptyParameters` | — |
| MF | `TOGGLE_FOCUS`, else `FOCUS_MODE_MANUAL` | `setToggleFocusMode`, `isSupportedToggleFocusMode`; the fallback needs none (Android `setFocusMode`) | `isSupportedToggleFocusMode()` is `true`; for the fallback, `getSupportedFocusModes` has `manual` |
| Burst (interval 0) | `BURST` | `setDriveMode`, `getSupportedBurstDriveSpeeds`, `setBurstDriveSpeed`, `setBurstDriveButtonReleaseBehave` | drive modes have `burst`, burst speeds not empty; otherwise single drive |

MF prefers the AF/MF toggle. The stubs have no getter for the toggle state, so the app reads the focus mode first: if the body is already in manual focus it leaves the toggle alone, otherwise it engages the toggle and releases it when the run ends. If the toggle call throws, it falls back to `setFocusMode("manual")` and restores the previous focus mode afterwards. `isSupportedLongExposureNR` and `getSupportedISOSensitivities` are read and logged for T12 and T14 but gate nothing yet. There is no shutter-speed list API in the stubs; the holy-grail ramp uses `Util.SHUTTER_SPEEDS`.

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

- Profile: SS off (upstream reports no silent shutter on the NEX-5R/5T, [`up#5`](https://github.com/jonasjuffinger/TimeLapse/issues/5)); quirk `DIAL_UNRELIABLE`, for T10.
- SS hidden even if the probe finds the methods.
- AEL, BRC3, holy grail, toggle focus and burst: the probe decides. Any of them may be missing on API 1.1. If they are, the option is hidden, and MF falls back to the focus mode.

CAPS results: pending.
