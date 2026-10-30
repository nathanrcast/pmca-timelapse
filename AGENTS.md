# pmca-timelapse — agent notes

TimeLapse+: an in-camera intervalometer for Sony PlayMemories (PMCA) cameras, hard-forked from jonasjuffinger/TimeLapse. Targets the a6500 (PMCA API 2.17) and NEX-5T (API 1.1). Plan, waves and ticket index: `docs/plan.md`. Ticket specs: `docs/tickets/T##-*.md`. PR/review process: `docs/workflow.md`.

## Commands

Gradle 8.7 (wrapper) + AGP 8.5.2. Needs a full JDK 17–21 (Gradle 8.7 cannot run on 22+, and a JRE fails in AGP's `androidJdkImage`) and an Android SDK with platform `android-34`, referenced from the untracked `local.properties` as `sdk.dir=<SDK path>`. CI: `.github/workflows/ci.yml` job `build`. Signing and publishing: `scripts/release.sh <version> [--local] [--dry-run]` (needs a gitignored `keystore.properties`, see `keystore.properties.example`).

```
JAVA_HOME=<JDK 17–21> ./gradlew clean :app:assembleDebug :app:assembleRelease :app:lint   # full build
JAVA_HOME=<JDK 17–21> ./gradlew :app:assembleDebug :app:lint                              # pre-push gate; + :core:test once T05 adds :core
python3 tools/analyze_log.py <LOG.CSV>                                                  # once T16 lands (stdlib only)
```

- Outputs: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed, v1+v2) and `app/build/outputs/apk/release/app-release-unsigned.apk` (unsigned until T18; T02 names release assets). Lint report: `app/build/reports/lint-results-debug.html`.
- API-10 guard: NewApi and InlinedApi are fatal lint issues. `assembleRelease` enforces them through `lintVitalRelease`; `assembleDebug` runs no lint, which is why the gate includes `:app:lint`.
- Check an APK: `$ANDROID_HOME/build-tools/34.0.0/aapt dump badging <apk> | head -3` must show package `io.github.nathanrcast.timelapse`, `sdkVersion:'10'` and `targetSdkVersion:'10'`. `unzip -p <apk> classes.dex | head -c 8 | od -c` must read `dex\n035`, the only dex version Dalvik 2.3 loads.
- OpenMemories-Framework is vendored in `app/libs/` at commit `f8df350` (sha256s in `app/libs/README.md`); there is no jitpack dependency. `stubs` must stay `compileOnly`: the `com.sony.*` classes come from the camera firmware, and packaging the stubs would shadow them.

## Layout

- `app/src/main/java/io/github/nathanrcast/timelapse/`: app sources; the Java namespace matches `applicationId`. `ShootActivity` holds the capture loop and the holy-grail ramp, `SettingsActivity` the setup UI, `Settings` the persisted options, and `Util` the shutter/ISO/deadband step tables.
- `app/src/main/java/com/github/ma1co/pmcademo/app/`: `BaseActivity` (Sony `ScalarInput` key handling) and `AppNotificationManager`, vendored from PMCADemo. They keep their own package and `LICENSE.txt`.

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
- The Frosbe fork is merged with its history (T03): `git log --follow` on a moved file runs through `com/frosbe/holygrail/` back to `com/jonasjuffinger/timelapse/`. Holy grail defaults to off, and its Sony calls run only when it is on. Its `com.jjoe64:graphview:3.1.4` dependency (LGPL, never shown) was removed.
- `local.properties` is machine-specific and untracked.
- Release minify is off on purpose; re-enabling it (R8) needs an on-camera Dalvik 2.3 test.

## Hardware

- An agent can't operate a camera. A `hw:required` acceptance box is only ticked from evidence the maintainer posts: a LOG/CAPS file from the card, or a photo. Never claim on-device verification.
- Camera logs land on the card under `TIMELPS/`.

## Review focus

1. Any Sony API call that skips the capability layer.
2. Any API > 10 or Java > 8 construct, which is a Dalvik `VerifyError` at load time.
3. Timing code that schedules relative to the previous shot rather than to `t0 + n·interval`.
4. UI options shown on bodies that don't support them.
5. Lab details or upstream `#N` refs leaking into tracked files or PR text.
