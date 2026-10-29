# T06: Drift-free capture scheduler

**Labels:** type:bug area:core size:M hw:required · **Milestone:** Wave 2 · **Depends on:** T05

## Goal
Replace the relative `postDelayed` loop with an absolute-deadline scheduler, which fixes interval drift, catch-up bursts and silently halved shot counts.

## Context
- `ShootActivity` currently computes `remainingTime = shootTime + interval - now`, where `shootTime` is the *previous* actual shot. It re-posts with a −150 ms fudge, and a 500 ms `manualShutterCallbackCallRunnable` hack covers a missing `onShutter` callback.
- Symptoms: drift and then bursts of up to 6 frames/s on NEX-5R-class bodies (`up#5`); every other shot missing when capture exceeds the interval (`up#26`, `up#50`).

## Design
- `core/.../Scheduler`: deadlines at `t0 + n·interval`.
  - At each tick it is either `FIRE` or `SKIP(reason)`.
  - It never queues more than one pending shot.
  - If capture is still busy at deadline *n*, that slot is a counted skip, and the next deadline is *n+1*. It never fires immediately to catch up.
  - Pause and resume shift `t0`.
- "Shots" means captured frames. The run ends when either the captured count or the elapsed slot count reaches the target, configurable as `stopOn = CAPTURED | SLOTS`, default `CAPTURED`.
- The Android side keeps one `Handler` timer re-armed per deadline, plus capture-complete via `onShutter`. Keep the existing fallback for bodies that don't fire `onShutter`, but drive it from the scheduler state.

## Acceptance
- [ ] Fake-clock JUnit tests for:
  - steady state
  - capture slower than the interval (skips counted, no bursts)
  - a single slow capture (no catch-up)
  - pause/resume
  - interval 0 (burst mode keeps working)
  - 10 000-slot long run with cumulative drift of 0
- [ ] `ShootActivity` has no scheduling arithmetic left; it calls `Scheduler`.
- [ ] **HW:** 100 shots @ 2 s on both bodies. EXIF timestamps (1 s resolution) show no bursts, and LOG.TXT shows no unexplained skips (pending maintainer).
