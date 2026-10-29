# T15: Long-run robustness + soak

**Labels:** type:bug area:camera size:L hw:required · **Milestone:** Wave 4 · **Depends on:** all of wave 3

## Goal
Multi-hour and multi-day runs complete unattended and leave the camera responsive.

## Context
- Reported upstream:
  - black screen until reboot after runs of more than a day (`up#52`)
  - intervals jitter between 90 % and 1000 % with 256 GB+ SDXC cards (`up#46`)
  - shots skipped on slower bodies
- The cameras auto-power-off unless the app keeps them awake.

## Tasks
1. Suppress auto power-off for the whole run, and verify it holds at 8 h.
2. Profile memory with `adb shell dumpsys meminfo` over Tweak ADB every 30 min of a run. Fix bitmap and preview leaks (review/thumbnail, and graphview from T03).
3. Large-card jitter: check whether `free_mb` and media-scanning calls block the capture path. Move them off-thread or sample them less often.
4. Turn the display off safely: make sure the DOFF path can always restore the display (`up#52`).
5. Add soak results to `docs/cameras.md`.

## Acceptance
- [ ] **HW:** a6500 runs 8 h @ 10 s and NEX-5T runs 4 h @ 10 s. `analyze_log.py` shows max drift < 100 ms and 0 unexplained skips, and the camera responds afterwards (pending maintainer).
- [ ] meminfo shows no monotonic heap growth across the run (log excerpt attached).
