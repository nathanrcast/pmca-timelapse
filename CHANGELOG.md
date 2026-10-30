# Changelog

## 2.0.0

First TimeLapse+ release: a hard fork of jonasjuffinger/TimeLapse. New application id (`io.github.nathanrcast.timelapse`), so it installs alongside upstream. Targets the a6500 and NEX-5T.

### Added

- Holy-grail exposure ramping, merged from the Frosbe fork with its history. Off by default.
- Scheduled start: start now, after a delay, or at a wall-clock time, with a visible countdown.
- Long-exposure NR guard: warns and raises the minimum interval when NR would halve the shot count.
- Shooting HUD: shots done and target, skips, next shot, elapsed time, battery, card space estimate and a run summary.
- Dial and wheel input with stepped values for every setting.
- Camera info screen (CapsDump) that writes `TIMELPS/CAPS-<model>.txt` with the body's methods and supported values.
- Per-shot `TIMELPS/LOG.CSV` and `tools/analyze_log.py`, which reports drift and unexplained skips.
- Capability layer: options a body does not support are hidden instead of called.
- Desktop assembly: `tools/assemble.sh` and `tools/frames_for_run.py` turn a run's frames into a clip; README rewritten for users; consolidated camera checklist.
- Soak tooling for long unattended runs.
- CI that builds unsigned APKs, and `scripts/release.sh` for local signing.
- Bulb exposure spike (feasible, unverified); no bulb feature ships in this release.

### Changed

- Capture loop rewritten around absolute deadlines (`t0 + n * interval`) in a pure-Java `:core` module with JVM tests.
- Exposure ramp moved into `:core`, one table step per slot, target in EV.
- Settings are stored as table indices; old slider keys are migrated.
- Modern Gradle/AGP build; OpenMemories-Framework is vendored, with no jitpack dependency.
- Press-and-hold slider increments, h/m/s duration display and delay countdown ported from 2026 forks.

### Fixed

- Failed captures are reported to the scheduler (`Scheduler.captureFailed`) instead of being lost.
- Camera auto power-off during a run: the power-off hold is re-asserted.
- Inconsistent intervals on large SDXC cards, from logging on the capture path; the logger is now asynchronous (upstream issue 46).
- Black screen after a long run: display-off state is restored safely (upstream issue 52).
- Interval drift and catch-up bursts, seen as bursts of up to 6 frames per second on NEX-5R-class bodies (upstream issue 5).
- Every other shot missing when capture takes longer than the interval (upstream issues 26, 50).
- Camera reboot from `NoSuchMethodError` on older bodies (upstream issue 4).
- Bracketed runs counting 3 per frame and ignoring the EV step (upstream issue 41).
- Inconsistent interval slider steps (upstream issues 8, 29) and no wheel support on some bodies.
- Start delay not honoured (upstream issue 36).
- Long-exposure NR silently halving the shot count (upstream issues 26, 34).
- Run not reliably stoppable (upstream issue 49), and no picture feedback while shooting (upstream issue 39).
- Shot-count integer overflow with infinite shots; holy-grail ramp bugs (target in EV, unknown ISO/shutter, slot-aware shutter clamp).

### Known limitations

- Nothing in this release is hardware-verified yet. The consolidated camera session is `docs/hw_test_checklist.md`; its results are posted on the open `hw:required` issues.
- Bulb exposures over 30 s are not supported.
