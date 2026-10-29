# T13: Shooting HUD + reliable stop

**Labels:** type:feature area:ui size:M hw:required · **Milestone:** Wave 3 · **Depends on:** T06, T07

## Goal
Show useful information while shooting, and make the run always stoppable.

## Tasks
- HUD fields:
  - shots done / target
  - skips
  - next shot in N s
  - elapsed / remaining
  - battery %
  - free card space (estimated shots left)
- Optional last-shot review thumbnail (`up#39`), shown for a configurable 0–5 s. Keep it off by default, because review costs time on slow bodies.
- MENU stops within one interval. Any key wakes the display under DOFF without stopping the run.
- On the power switch or `onPause`, cancel timers, stop preview and release the camera, so the camera powers down normally (`up#49`).
- Review upstream's `testing` branch commit "display battery save mode and switchDisplayOutput" and adopt it if it works.

## Acceptance
- [ ] The HUD-model formatting is unit-tested in `:core`.
- [ ] **HW:** checklist rows 5–6 pass on both bodies (pending maintainer).
