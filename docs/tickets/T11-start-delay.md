# T11: Start delay and scheduled start

**Labels:** type:feature area:ui size:S hw:required · **Milestone:** Wave 3 · **Depends on:** T04, T06

## Goal
Start shooting after a delay, or at a wall-clock time, with a visible countdown (`up#36`).

## Tasks
- Settings: `start = NOW | DELAY(min) | AT(hh:mm)`. AT means the next occurrence, using the camera clock via the framework `DateTime`.
- Reuse the countdown from T04 if it landed. During the countdown the screen is off when DOFF is on, and any key wakes it.
- The scheduler's `t0` is the scheduled start, not the button press.
- Keep the camera from auto-powering-off during the countdown. Use the same mechanism as during shooting; T15 hardens it.

## Acceptance
- [ ] Unit test: an AT time that has already passed today rolls to tomorrow.
- [ ] **HW:** a 2-minute delay on both bodies, with the first frame within 1 s of the countdown reaching zero (pending maintainer).
