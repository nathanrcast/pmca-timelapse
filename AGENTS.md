# pmca-timelapse — agent notes

TimeLapse+: an in-camera intervalometer for Sony PlayMemories (PMCA) cameras, hard-forked from jonasjuffinger/TimeLapse. Targets the a6500 (PMCA API 2.17) and NEX-5T (API 1.1). Plan, waves and ticket index: `docs/plan.md`. Ticket specs: `docs/tickets/T##-*.md`. PR/review process: `docs/workflow.md`.

## Commands

Gradle 8.7 (wrapper) + AGP 8.5.2. Needs a full JDK 17–21 (Gradle 8.7 cannot run on 22+, and a JRE fails in AGP's `androidJdkImage`) and an Android SDK with platform `android-34`, referenced from the untracked `local.properties` as `sdk.dir=<SDK path>`. CI: `.github/workflows/ci.yml` job `build`. Release and signing: `docs/release.md` (`scripts/release.sh`; needs a gitignored `keystore.properties`).

```
JAVA_HOME=<JDK 17–21> ./gradlew clean :core:check :app:assembleDebug :app:assembleRelease :app:lint :app:testDebugUnitTest  # full build = pre-push gate
JAVA_HOME=<JDK 17–21> ./gradlew :core:test  # JVM unit tests only
python3 -I -m unittest discover -s tools -p 'test_*.py'  # desktop-tool tests (stdlib only; CI runs them too)
```

- Desktop tools (`tools/analyze_log.py`, `tools/assemble.sh`, `tools/frames_for_run.py`): usage in `docs/architecture.md` § Desktop tools.
- `:core:check` runs `:core:test` plus a JaCoCo gate: it fails below 90 % line coverage on `ShotPlan`, `Scheduler`, `Progress`, `RampController` or `ExposureRamp`. Report: `core/build/reports/jacoco/test/`.
- API-10 guard: NewApi and InlinedApi are fatal lint issues. `assembleRelease` enforces them through `lintVitalRelease`; `assembleDebug` runs no lint, which is why the gate includes `:app:lint`.
- Check an APK: `$ANDROID_HOME/build-tools/34.0.0/aapt dump badging <apk> | head -3` must show package `io.github.nathanrcast.timelapse`, `sdkVersion:'10'` and `targetSdkVersion:'10'`. `unzip -p <apk> classes.dex | head -c 8 | od -c` must read `dex\n035`, the only dex version Dalvik 2.3 loads.
- OpenMemories-Framework is vendored in `app/libs/` at commit `f8df350` (sha256s in `app/libs/README.md`); there is no jitpack dependency. `stubs` must stay `compileOnly`: the `com.sony.*` classes come from the camera firmware, and packaging the stubs would shadow them.

## Layout

Class-by-class map: `docs/architecture.md`.

- `app/src/main/java/io/github/nathanrcast/timelapse/`: Activities and Android glue; `camera/` under it is the capability layer (`SafeCamera`, `ReflectiveCapabilities`, `ModelProfile`; probe rules in `docs/cameras.md`).
- `core/`: plain `java-library` (`--release 8`) with no Android or Sony jar on its classpath; holds `Settings`, `ShotPlan`, `Scheduler`, `ExposureRamp` and the `core.camera` capability model. All `Scheduler` calls come from one thread (the main looper).
- `app/src/main/java/com/github/ma1co/pmcademo/app/`: vendored from PMCADemo; keeps its own package and `LICENSE.txt`.

## Invariants

- **Runtime is Dalvik on both cameras, but not the same Android:** the NEX-5T runs 2.3.7 (API 10), the a6500 4.1.2 (API 16). Keep `minSdk`/`targetSdk` at 10, so the code must be API-10-safe. Don't use any API above 10, and no AndroidX, Kotlin stdlib or core-library desugaring. Java language level is 8 at most.
- **Only `camera/` touches `CameraEx`, `ParametersModifier` or `CameraSequence`; everything else goes through `SafeCamera`.** A method missing on an older body throws `NoSuchMethodError` and reboots the camera (upstream issue 4). So a new Sony call needs:
  - a `SonyMethod` entry, which also puts it in the CAPS *Used methods* report;
  - a `caps.has()`/`caps.supports()` guard before the call. For the options `configure()` applies, the guard is `RunOptions.mask`, the single gate, which `ShootActivity` runs against the session's capabilities;
  - for any Sony interface it implements, a small class of its own in `camera/` that is only loaded once the probe has found the method.

  `CapsDumpActivity` and `CapsFormat` are exempt: they are the probe tool, and they catch `Throwable` per call. Check: `grep -rn "CameraEx\|ParametersModifier" app/src/main/java | grep -v /camera/ | grep -v CapsDump | grep -v CapsFormat` must print nothing.
- **`DisplayManager` calls outside `BaseActivity` are `Display`'s alone** (DOFF switch and restore). They are `SonyMethod` entries, and `Display` guards each with `ReflectiveCapabilities.has` because `restoreIfLeftOff` runs before any probe. Every restore path targets the panel.
- `applicationId` and the release signing key are permanent. Installers pin the certificate on first install, so changing either forces every user to uninstall.
- Pure logic (scheduling, exposure ramp, shot-plan math) belongs in `:core` with JVM unit tests, not in Activities.
- **Lint's API-10 check does not cover `:core`** (Android lint runs NewApi only on the app module). Keep core code to what API 10 has: no `java.util.function`, streams, `java.time` or `java.nio.file`, and anonymous classes rather than lambdas. After changing `:core`, check the APK with `dexdump -d`: there must be no `invoke-custom`/`invoke-polymorphic`.

## Settled decisions — do not re-litigate

- This is a hard fork (standalone repo), not a rewrite. Upstream history is preserved.
- No in-camera video encoding: API 10 has no MediaCodec. Assembly happens on the desktop (`tools/assemble.sh`, T17).
- CI builds unsigned APKs. Release signing is local only, and the keystore never goes into CI secrets.

## Traps

- **Upstream issue numbers ≠ ours.** In issues, PRs and commit messages, write upstream refs as `` `up#41` `` inside backticks. A bare `#41` links to *our* issue 41. A `jonasjuffinger/TimeLapse#41` or upstream issue URL posts a backlink on the dormant upstream repo. Link to upstream issues only from files under `docs/`.
- Don't `@`-mention upstream authors in issues/PRs. Credits live in README.
- Long-exposure NR makes the camera shoot a dark frame after each exposure, which shows up as "skipped every other shot" (upstream issues 26/50). Rule it out before blaming the scheduler.
- The Frosbe fork is merged with its history: `git log --follow` on a moved file runs through `com/frosbe/holygrail/` back to `com/jonasjuffinger/timelapse/`. Holy grail defaults to off, and its Sony calls run only when it is on.
- `local.properties` is machine-specific and untracked.
- Release minify is off on purpose; re-enabling it (R8) needs an on-camera Dalvik 2.3 test.

## Hardware

- An agent can't operate a camera. A `hw:required` acceptance box is only ticked from evidence the maintainer posts: a LOG/CAPS file from the card, or a photo. Never claim on-device verification.
- Camera logs land on the card under `TIMELPS/`. Session evidence is committed under `docs/camera-sessions/<body>-<date>/`.
- **`System.currentTimeMillis()` is time since boot on the cameras** (1970 dates). Real wall time comes only from `Clocks.WALL` (the camera RTC through OpenMemories `DateTime`, system clock as the logged fallback); never call `currentTimeMillis()` for a run id, header or file comparison.

## Review focus

1. Any Sony API call that skips the capability layer.
2. Any API > 10 or Java > 8 construct, which is a Dalvik `VerifyError` at load time.
3. Timing code that schedules relative to the previous shot rather than to `t0 + n·interval`.
4. UI options shown on bodies that don't support them.
5. Lab details or upstream `#N` refs leaking into tracked files or PR text.
