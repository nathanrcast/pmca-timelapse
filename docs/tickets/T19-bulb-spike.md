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
