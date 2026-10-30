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

## Inherited from T03
The Frosbe fork merge (#23) left these shoot-screen and stop differences from upstream. They apply with holy grail off too:
- The end screen is gone. A finished run returns to the settings screen about 2 s after the last shot, or right after a burst. Upstream showed "Thanks for using this app!" / "Press MENU to exit" (`llEnd`, removed from `activity_shoot.xml`).
- `stopPicturePreview = true` is commented out in the final-shot branch of `ShootActivity.onShutter`. The runnable no longer stops the last preview or applies DOFF; `onPause` tears the preview down as the activity exits.
- The burst-mode HUD shows `1/N` in the big counter (the shot count stays at 1 and N is the duration in seconds) instead of the remaining seconds, and moves the remaining seconds to the second field.
- The settings screen is one `ScrollView`. Start/Close follow the content instead of being pinned to the bottom of the screen.

## Status
Implemented (PR pending merge); the `hw:required` boxes wait for the camera session.
- HUD: `HudModel` in `:core`; `CardSpace` samples free space and the average size of this run's JPEG/ARW files off the main looper.
- Review: setting `reviewSeconds` 0-5, default 0 (none); with 0, DOFF applies right after each capture.
- Stop: MENU stops at once, toasts the summary and returns to settings. Any other key only wakes the display (back to DOFF after 5 s). `ShootActivity.teardown()` is the single idempotent path for MENU, `onPause` (power switch) and `onDestroy`.
- Inherited from T03: end summary screen (2 s or any key), final-shot preview stopped, burst counter already shows elapsed/length seconds via `Progress`, Start/Close/Camera info pinned in a bottom bar.
- Upstream `testing` commit ("display battery save mode and switchDisplayOutput") not adopted: it calls `DisplayManager.setSavingBatteryMode` with no capability guard or `SonyMethod` entry (a missing method reboots the camera), adds a manual panel/finder toggle that can strand the output on a device the body lacks, and was an unfinished experiment (its own message says "Testing"). Our `Display` already uses `switchDisplayOutputTo` for DOFF. Revisit only with a probe-backed `SonyMethod` and an on-camera check of the saving gain.
