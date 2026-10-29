# T09: BRC3 bracket counting and EV

**Labels:** type:bug area:camera size:M hw:required · **Milestone:** Wave 3 · **Depends on:** T06, T07

## Goal
Bracketed timelapses count and expose correctly: one interval = one 3-frame bracket, at the EV step the user chose on the camera.

## Context
`up#41`, observed on an a6000: with 2 shots requested, the app fires 2 frames in quick succession and counts 3, pauses, fires 2 more and counts 6. The EV step is always 3.0 regardless of the drive mode set (0.3/0.7/1.0/2.0/3.0 EV).

## Tasks
1. Find out how the app triggers bracketing: drive-mode passthrough, or exposure compensation set per frame. Then read the drive mode and bracket step through `SafeCamera`/`Capabilities`.
2. A bracket is one scheduler slot. `bracket_idx` 0..2 goes into LOG.CSV (T16), and the slot completes after the third `onShutter`.
3. Auto-detect bracket mode, so the BRC3 checkbox is redundant when the drive mode is already 3-frame bracketing. Keep the checkbox only if detection is impossible on a body, and record that in its profile.
4. Warn when the interval is shorter than the estimated bracket duration (3 × shutter + write time, measured from the last bracket).

## Acceptance
- [ ] Scheduler/bracket unit tests: N slots give 3N frames with no double counting.
- [ ] **HW:** a6500 and NEX-5T, 10 shots @ 0.7 EV bracket gives 30 files whose EXIF exposure spreads by 0.7 EV (pending maintainer).
