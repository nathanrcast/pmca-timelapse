# T12: Long-exposure NR guard

**Labels:** type:bug area:camera size:S hw:required · **Milestone:** Wave 3 · **Depends on:** T07

## Goal
Stop long-exposure NR from silently halving the shot count (`up#26`, `up#34`).

## Tasks
- Read the LENR state through `Capabilities`. If it is on and shutter ≥ 1 s (where the camera applies NR), then:
  - if the body supports setting LENR, offer "Turn LENR off for this run" and restore the user's setting afterwards;
  - otherwise show a blocking warning and raise the minimum interval to `2 × shutter + 1 s`.
- Log the LENR state in the LOG.CSV header.

## Acceptance
- [ ] Unit test for the minimum-interval rule.
- [ ] **HW:** a6500 with LENR on, 2 s shutter, 3 s interval: the warning appears, and with LENR turned off by the app every slot fires (pending maintainer).
