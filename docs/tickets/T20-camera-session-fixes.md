# T20: Camera-session fixes (a6500 findings)

## Goal
Fix what the first a6500 camera session exposed.

## Findings (a6500 ILCE-6500 fw 1.04, Android 4.1.2 / SDK 16)
1. **Wall clock reads as 1970.** `System.currentTimeMillis()` on the camera is time since boot (`run_id 19700101-012144`, `uptime_to_wall_ms=1`, CAPS header "written 1970-01-01"). The camera's own clock (OpenMemories framework `DateTime`, EXIF `DateTimeOriginal` 2026:10:31 08:24) is the only real wall time. This breaks run ids, LOG.CSV wall conversion (so `frames_for_run.py --run` can't match files), CardSpace "files since run start", and CAPS timestamps.
2. **LOG.CSV header model/firmware** come from `Build.*` (`model=ScalarA`, `firmware=59`) instead of `DeviceInfo` (`ILCE-6500`, `1.04`).
3. **CAPS file fell back to `CAPS.TXT`.** Find out why `CAPS-ILCE-6500.txt` failed and log the exception.
4. **BRC3 produced no EV spread** (5 frames, all 1/160 / bias 0 / ISO 640) and 9 FIREs gave only 5 files, with silent shutter on. Log the `onShutter` status per frame in LOG.CSV/LOG.TXT. If silent shooting is incompatible with bracketing, mask BRC3 or warn when both are on. Re-test with SS off.
5. **First-shot latency** of 112 ms fails the 100 ms drift gate; later slots are 2–17 ms. Add a short settle (about 500 ms) between `configure()` and slot 0, or report the first slot separately in the analyzer. Pick one.
6. **Docs:** the a6500 runs Android 4.1.2 (SDK 16), not 2.3.7. Fix AGENTS.md, docs/plan.md and docs/cameras.md, and fill the ILCE-6500 section of docs/cameras.md from the CAPS file (methods, values, key codes).
7. **Timezone:** card file mtimes as seen by Linux are 7 h off EXIF time (exFAT local time vs host TZ). Document `--offset-s` for this, or prefer EXIF `DateTimeOriginal` in `frames_for_run.py`.

## Acceptance
- [ ] Unit tests for the clock source abstraction and header fields
- [ ] **HW:** a6500 rerun shows a real run_id date, a correct header, the CAPS long filename, and an EV spread with BRC3 + SS off (pending maintainer)



## Added after the NEX-5T session (same date)
The NEX-5T logs are in `docs/camera-sessions/nex5t-2026-10-31/` (fw 1.01, PMCA API 1.1, Android 2.3.7 / SDK 10; 40/45 methods present, 10/10 @ 1 s and 40/40 @ 10 s with 0 skips, first slot +128 ms, same 1970 clock).
9. **Dial direction by scan code.** The NEX-5T reports `ISV_DIAL_1_CLOCKWISE` (525) with status -22 and `COUNTERCW` (526) with +22, the opposite sign of the a6500. Direction comes from the scan code (`core` `DialDirection`); the status only gives the size of a multi-notch event.
10. **`DIAL_UNRELIABLE` dropped from the NEX profile.** The control wheel delivers `ISV_DIAL_KURU_*` (522/523, +-22). The enum value stays, with no profile using it.
11. **Continuous-bracket support.** A body that only offers `EXPOSURE_BRACKET_MODES=[continue]` gets BRC3: `single` if available, else `continue`. In continue mode one `takePicture` shoots all three frames and the slot completes after the third `onShutter`, or after the first plus a timeout. Every callback and its status is logged. Hardware-pending.
12. **`docs/cameras.md` NEX-5T section** filled from its CAPS file.

## Status
All code and docs items are implemented. The HW acceptance box and the continuous-bracket behaviour stay pending the maintainer's next session: see "What the next camera session should check" in the PR.
