# Camera bodies

What each supported body's Sony API actually offers, from its CAPS file (T08). T07 summarizes each file here, and T10/T19 read their key codes and long-exposure findings from it.

## How to capture CAPS files

1. Install the current build and launch TimeLapse+.
2. On the settings screen, select **Camera info** in the bottom bar (between Close and Start).
3. The app opens the camera, reads everything without changing any setting, releases the camera and writes `TIMELPS/CAPS-<model>.txt` to the card. The path appears on screen. If the card rejects the long name, the file is `TIMELPS/CAPS.TXT`. Each run overwrites the file.
4. When the screen reads *Turn every dial and press every button; hold MENU 2 s to exit*: turn the control wheel and every dial one notch each way, step the mode dial through every position, and press every button (half and full shutter included; nothing is acted on). Each event is appended to the file and the last 15 show on screen.
5. Hold MENU for 2 s, then release it to return to settings. A short MENU press is only logged.
6. Copy `TIMELPS/CAPS-*.txt` off the card and attach it to the T08 issue.

A CAPS file has these sections:

- **Device**: `DeviceInfo` (model, firmware, PMCA API version) and `android.os.Build`. The serial number is omitted.
- **Used methods**: every Sony method the app calls today, each marked `present` or `ABSENT` on this firmware.
- **Classes**, **Methods**, **Constants**: every public method signature of `CameraEx`, `CameraEx$ParametersModifier`, the `CameraEx` listener and callback interfaces, `CameraSequence` (if present) and the other Sony classes the app touches, plus the runtime values of the `CameraEx` and `ParametersModifier` constants.
- **ScalarInput ISV_\* constants**: the firmware's key-code table.
- **Values**: the result of every no-arg `get*`/`is*` on `ParametersModifier`, `Camera.Parameters` and `AutoPictureReviewControl`, one per line as `name = value` or `name !! <exception>`, plus `Camera.Parameters.flatten()`.
- **Key log**: seconds since start, `down`/`up`/`repeat`, keyCode, scanCode, the `ScalarInput` constant name, and the dial position for dial events.

## ILCE-6500

PMCA API 2.17. Pending the maintainer's CAPS file (T08); T07 fills this in.

## NEX-5T

PMCA API 1.1. Pending the maintainer's CAPS file (T08); T07 fills this in.
