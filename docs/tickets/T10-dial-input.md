# T10: Dial/wheel input + stepped values

**Labels:** type:feature area:ui size:M hw:required · **Milestone:** Wave 3 · **Depends on:** T07, T08

## Goal
Every setting can be changed quickly with each body's control wheel or dial, using sensible value steps.

## Context
- On NEX-5R-class bodies the wheel isn't read, so only left/right work, and the slider jumps 1→5→9 s (`up#5`). Slider steps are inconsistent (`up#8`, `up#29`).
- Key codes come from T08's CAPS files. `BaseActivity` already routes `ScalarInput` keys.

## Design
- Per-model key map in `ModelProfile`:
  - wheel CW/CCW → next/previous value
  - up/down → focus the next or previous setting
  - press-and-hold accelerates (T04)
- Stepped value tables in `:core`:
  - interval: 0 (burst), 0.5, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 45, 60, 90, 120, 180, 300 s
  - shots: 10–100 by 10, 100–1000 by 50, 1000–10000 by 500, then ∞
  - delay: 0–60 min
- The seek bars show an index into the table.

## Acceptance
- [ ] Unit tests for table navigation, including clamping and the burst/∞ ends.
- [ ] **HW:** on both bodies, every setting changes with the wheel in both directions and with the arrow keys (pending maintainer).
