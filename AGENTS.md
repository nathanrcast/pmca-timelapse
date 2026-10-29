# pmca-timelapse — agent notes

TimeLapse+: an in-camera intervalometer for Sony PlayMemories (PMCA) cameras, hard-forked from jonasjuffinger/TimeLapse. Targets the a6500 (PMCA API 2.17) and NEX-5T (API 1.1). Plan, waves and ticket index: `docs/plan.md`. Ticket specs: `docs/tickets/T##-*.md`. PR/review process: `docs/workflow.md`.

## Commands

The build is still upstream's AGP 3.5 / jcenter setup until T01 lands. T01 then owns this section, along with T02 for CI. After T01 the pre-push gate is:

```
./gradlew :app:assembleDebug :app:lint        # + :core:test once T05 adds :core
python3 tools/analyze_log.py <LOG.CSV>        # once T16 lands (stdlib only)
```

## Layout

- `app/src/main/java/com/jonasjuffinger/timelapse/`: upstream sources. `ShootActivity` holds the capture loop and `SettingsActivity` the setup UI.
- `app/src/main/java/com/github/ma1co/pmcademo/app/BaseActivity.java`: Sony `ScalarInput` key handling, inherited from PMCADemo.
- The Java namespace is still `com.jonasjuffinger.timelapse`, and only `applicationId` was changed (to `io.github.nathanrcast.timelapse`). T03 settles the final namespace.

## Invariants

- **Runtime is Android 2.3.7 Dalvik (API 10) on both cameras.** Keep `minSdk`/`targetSdk` at 10. Don't use any API above 10, and no AndroidX, Kotlin stdlib or core-library desugaring. Java language level is 8 at most.
- **Never call a Sony `CameraEx`/`ParametersModifier` method directly** once T07 lands. Go through the capability layer. A method missing on an older body throws `NoSuchMethodError` and reboots the camera (upstream issue 4).
- `applicationId` and the release signing key are permanent. Installers pin the certificate on first install, so changing either forces every user to uninstall.
- Pure logic (scheduling, exposure ramp, shot-plan math) belongs in `:core` with JVM unit tests, not in Activities.

## Settled decisions — do not re-litigate

- This is a hard fork (standalone repo), not a rewrite. Upstream history is preserved.
- No in-camera video encoding: API 10 has no MediaCodec. Assembly happens on the desktop (`tools/assemble.sh`, T17).
- CI builds unsigned APKs. Release signing is local only, and the keystore never goes into CI secrets.

## Traps

- **Upstream issue numbers ≠ ours.** In issues, PRs and commit messages, write upstream refs as `` `up#41` `` inside backticks. A bare `#41` links to *our* issue 41. A `jonasjuffinger/TimeLapse#41` or upstream issue URL posts a backlink on the dormant upstream repo. Link to upstream issues only from files under `docs/`.
- Don't `@`-mention upstream authors in issues/PRs. Credits live in README.
- Long-exposure NR makes the camera shoot a dark frame after each exposure, which shows up as "skipped every other shot" (upstream issues 26/50). Rule it out before blaming the scheduler.
- The Frosbe fork moved sources to `com/frosbe/holygrail/` and added `com.jjoe64:graphview:3.1.4`.
- `local.properties` is machine-specific and untracked.

## Hardware

- An agent can't operate a camera. A `hw:required` acceptance box is only ticked from evidence the maintainer posts: a LOG/CAPS file from the card, or a photo. Never claim on-device verification.
- Camera logs land on the card under `TIMELPS/`.

## Review focus

1. Any Sony API call that skips the capability layer.
2. Any API > 10 or Java > 8 construct, which is a Dalvik `VerifyError` at load time.
3. Timing code that schedules relative to the previous shot rather than to `t0 + n·interval`.
4. UI options shown on bodies that don't support them.
5. Lab details or upstream `#N` refs leaking into tracked files or PR text.
