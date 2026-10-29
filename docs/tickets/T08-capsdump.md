# T08: Spike — CapsDump diagnostics mode

**Labels:** type:spike area:camera size:M hw:required · **Milestone:** Wave 1 · **Depends on:** T01

## Goal
Collect the facts T07 and T10 need from each body in a single maintainer session.

## Design
A hidden `CapsDumpActivity`, reached by holding a key combination on the settings screen (pick one and document it, e.g. long-press DELETE). It writes `TIMELPS/CAPS-<model>.txt` containing:
1. `DeviceInfo`: model, firmware version, PMCA API version, Android `Build.*`.
2. A reflection probe of **every public method** on `CameraEx`, `CameraEx.ParametersModifier`, `CameraEx.ParametersModifier`'s supported-value getters and `CameraSequence` (if present): one line each, `present`/`absent`.
3. For each supported-value getter that exists: the value list (shutter speeds, ISO, drive modes, focus modes, silent shutter, LENR / long-exposure NR, picture-review settings, bracket steps).
4. **Key logger:** a full-screen live view of `ScalarInput` key codes. It appends each key-down/up/repeat with its code and scan name for every button, the control wheel and any dial, until MENU is held for 2 s.

Don't touch any existing class beyond adding the entry point.

## Acceptance
- [ ] Builds and lints. The probe catches `Throwable` per call, so a missing method can't crash it.
- [ ] Unit-test the formatter on a fake.
- [ ] **HW:** the maintainer runs it on both bodies, turns every control one notch each way, and attaches both CAPS files to this issue (pending maintainer).
