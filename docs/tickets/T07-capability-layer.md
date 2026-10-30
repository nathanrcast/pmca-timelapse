# T07: Capability layer + per-model profiles

**Labels:** type:refactor area:camera size:L hw:required · **Milestone:** Wave 2 · **Depends on:** T03, T08

## Goal
No Sony API call can crash or reboot an older camera, and the UI shows only the options the body actually supports.

## Context
- Upstream rebooted the a6000 with `NoSuchMethodError: CameraEx$ParametersModifier.isSupportedSilentShutterMode` (`up#4`). The NEX-5T is PMCA API 1.1, far older than the a6500's 2.17.
- T08's `CAPS-NEX-5T.txt` and `CAPS-ILCE-6500.txt`, attached to the T08 issue, are the ground truth.

## Design
- `camera/ReflectiveCapabilities` (the probe, run by `SafeCamera`):
  - built once per session
  - probes every `CameraEx`, `CameraEx.ParametersModifier` and `CameraSequence` method the app uses, via reflection (`getMethod`, then catch `NoSuchMethodException`/`NoSuchMethodError`/`LinkageError`)
  - reads supported value lists where an API offers them: silent shutter, AEL, focus modes, drive/bracket modes, LENR, shutter-speed range, ISO list
- `camera/ModelProfile`: static overrides keyed on `DeviceInfo.getModel()` (`NEX-5T`, `ILCE-6500`, default). Use them for quirks reflection can't see, such as "method exists but no-ops" or "needs continuous drive".
- `camera/SafeCamera`: the only class that touches `CameraEx` (`CapsDump` excepted, being the probe tool). Every call returns a result or a typed failure, and failures are logged once per run.
- The UI binds option visibility to the capabilities.
- Expose a pure-Java `Capabilities` interface so the logic can be tested with a fake. It lives in `camera/model` (no `android.*`/`com.sony.*` imports) and moves to `:core` once T05 lands.

## Acceptance
- [ ] `grep -rn "CameraEx\|ParametersModifier" app/src/main/java | grep -v /camera/ | grep -v CapsDump | grep -v CapsFormat` returns nothing.
- [ ] A unit test with a fake that is missing every optional method shows the UI model hides SS/AEL/BRC3 and nothing throws.
- [ ] Both `CAPS-*.txt` files are summarized in `docs/cameras.md`.
- [ ] **HW:** on the NEX-5T, every visible option works and SS is hidden; on the a6500, SS works and is visible (pending maintainer).

## Inherited from earlier tickets
- The holy-grail checkbox is shown on every body. HG on calls `setProgramLineRangeOverListener`, `ParametersModifier.getShutterSpeed`/`getISOSensitivity`, `CameraEx.incrementShutterSpeed`/`decrementShutterSpeed` and `setISOSensitivity`, any of which may be missing on PMCA API 1.1. Hide the HG checkbox unless all of them are present, so that ticking it can't cause a `NoSuchMethodError` on the NEX-5T. T03 only kept these calls off the HG-off path, with ad-hoc `if (settings.holyGrail)` gating that this layer replaces.
- Toggle-focus MF (`ParametersModifier.isSupportedToggleFocusMode` / `CameraEx.setToggleFocusMode`): support it through the capability layer, saving and restoring the user's prior toggle state, with a fallback to `FOCUS_MODE_MANUAL` if it fails (dropped from T04).
