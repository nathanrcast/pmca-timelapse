# T19: Stretch spike — bulb exposures over 30 s

**Labels:** type:spike area:camera size:M hw:required · **Milestone:** Wave 4 · **Depends on:** T08

## Goal
Find out whether the app can time bulb exposures (> 30 s) for star trails and night lapses (`up#13`, `up#20`).

## Tasks
- From T08's probe, find any bulb or long-exposure API (shutter "BULB" value, `CameraSequence` start/stop exposure).
- If one exists, prototype `exposure = N s` in bulb mode on the a6500, with the scheduler slot covering the exposure plus write time.
- If none exists, document why and close the ticket.

## Acceptance
- [ ] A finding written up in `docs/cameras.md`, either way.
- [ ] If feasible: **HW** 10 × 60 s bulb frames on the a6500 (pending maintainer).

## Status

Spike written up in `docs/cameras.md` (section "Bulb exposures (T19 finding)"). Verdict: **feasible-unverified**. The stubs have no BULB constant, but `adjustShutterSpeed`/`incrementShutterSpeed` plus `cancelExposure`/`stopDirectShutter` and the `ShutterSpeedChangeListener`/`ExposureCompleteListener`/`StoreImageCompleteListener` callbacks cover the flow, and public reports say bulb works in single drive mode. The bulb sentinel and the stop call need one a6500 session. The HW acceptance box stays open (pending maintainer).

## Follow-up implementation

New ticket, depends on a CAPS file from the a6500 with the Bulb probe.

- `core/camera/SonyMethod.java`: add the eight entries listed in the finding; add `Feature.BULB` with its rules, tests in `core/src/test/` (feature on/off by method set and drive-mode list).
- `core`: bulb slot math in `ShotPlan`/`Settings` (`exposure * (NR ? 2 : 1) + write margin`, minimum interval, shots remaining), with JVM tests holding the 90 % `ShotPlan` coverage gate; exposure slider range above 30 s, shown only when `Feature.BULB` is on.
- `camera/SafeCamera.java`: `selectBulb()`, `startBulb()`, `endBulb()`, the two completion-listener setters, each guarded by `caps.has(...)`, with listener bridge classes in `camera/`.
- `ShootActivity`: bulb branch in the capture loop. Start, post a timer for N s on `Clocks.UPTIME`, call `endBulb()`, wait for `StoreImageCompleteListener` or the slot timeout, log both, then schedule the next shot from the slot start. Release cleanly on exit, as for the cancel path.
- `CapsDumpActivity`/`CapsFormat`: the Bulb probe step and the extra *Values*.
- `ModelProfile`: switch `BULB` off on any body whose probe fails.
- `docs/hw_test_checklist.md`: add the five-step test plan.
- Re-run the invariants grep in `AGENTS.md` and the full pre-push gate.
