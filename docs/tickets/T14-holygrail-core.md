# T14: Holy-grail ramp into `:core`

**Labels:** type:refactor area:core size:M hw:required · **Milestone:** Wave 3 · **Depends on:** T05, T07

## Goal
Make the exposure-ramping logic from T03 testable and model-aware.

## Tasks
- Move the ramp decision into `core/.../ExposureRamp`. It takes the measured exposure, target, current SS/ISO, limits (max SS ≤ interval/2, ISO cap) and deadband, and returns the next SS/ISO. It is a pure function with no camera access.
- Take shutter/ISO step tables from `Capabilities` (T07) per body. The NEX-5T and a6500 ranges differ.
- Cover the Frosbe fork's own fix history: the "sunrise overexpose" and "ISO last change" issues. Add tests that reproduce them.
- Limit the ramp to ≤ 1/3 EV per shot to avoid flicker. Make the limit configurable.

## Acceptance
- [ ] Unit tests:
  - sunset ramp (SS lengthens first, ISO only after max SS)
  - sunrise (ISO drops first)
  - deadband (no change for small deltas)
  - limit clamping
- [ ] **HW:** a sunset run on the a6500 gives a smooth exposure curve in the LOG.CSV `ss`/`iso` columns (pending maintainer).

## Inherited from T03
Holy-grail bugs that came in with the Frosbe fork merge (#23) and were not fixed there:
- The manual target exposure is stored in 1/3-EV steps (`Settings.targetExposure`, −30…30, shown divided by 3 as EV). `ShootActivity` compares it directly with `exposureLevel`, which is in EV, so a manual target lands 3× too far from 0. "Use current exposure", the default, is unaffected.
- The target slider (`laTargetExposure`) stays hidden on load when "Use current exposure" is saved off. Its visibility is only applied when the checkbox changes.
- The default limits are `maxShutterSpeedIndex = 0` (1/8000) and `maxISOIndex = 0` (ISO 50), so with untouched settings the ramp can never brighten.
- The ramp calls the Sony API directly: `decrementShutterSpeed`/`incrementShutterSpeed`, `setISOSensitivity`, `getShutterSpeed`, `getISOSensitivity` and `setProgramLineRangeOverListener`. These move behind T07's capability layer.
- The exposure history (`exposureLevelList`) grows by one entry per HG shot for the whole run, but only the last `averageExposureAmount` entries are read.
