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
