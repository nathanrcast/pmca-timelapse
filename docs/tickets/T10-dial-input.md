# T10: Dial/wheel input + stepped values

**Labels:** type:feature area:ui size:M hw:required · **Milestone:** Wave 3 · **Depends on:** T07, T08

## Goal
Every setting can be changed quickly with each body's control wheel or dial, using sensible value steps.

## Context
- On NEX-5R-class bodies the wheel isn't read, so only left/right work, and the slider jumps 1→5→9 s (`up#5`). Slider steps are inconsistent (`up#8`, `up#29`).
- Key codes come from T08's CAPS files. `BaseActivity` already routes `ScalarInput` keys.

## Design
- Input routing is identical on every body (decision: no per-model key map in `ModelProfile`). `SettingsActivity` sends every dial and the control wheel (`ISV_DIAL_1/2/3_*`, `ISV_DIAL_KURU_*`) through the `BaseActivity` hooks to one action:
  - clockwise or counter-clockwise → next or previous value of the focused seek bar or spinner; a focused checkbox toggles on any direction
  - up/down → move focus to the previous or next setting (default focus search)
  - left/right step a focused bar; press-and-hold repeats one table entry at about 6 per second (T04's hold handling, rescaled for index bars)
- `DIAL_UNRELIABLE` (`NEX-*`) is documented only: the arrow keys are the guaranteed path, so no body depends on its wheel.
- Stepped value tables in `:core` (`StepTables`); the seek bars show an index into them:
  - interval: 0 (burst), 0.5, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1200, 1800, 2400 s
  - shots (burst length in seconds when burst): 1, 2, 3, 5, 10–100 by 10, 100–1000 by 50, 1000–10000 by 500, then ∞; default 10
  - delay: 0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440 min
- Interval, shots and delay persist as values (`intervalMs`, `shots`, `delayMin`). Installs holding the old raw slider positions are migrated on first load, snapped to the nearest table entry (`LegacySliderMapping`).

## Acceptance
- [ ] Unit tests for table navigation, including clamping and the burst/∞ ends.
- [ ] **HW:** on both bodies, every setting changes with the wheel in both directions and with the arrow keys (pending maintainer).
