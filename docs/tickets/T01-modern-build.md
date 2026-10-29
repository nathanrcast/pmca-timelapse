# T01: Spike — modern build toolchain

**Labels:** type:spike area:build size:L hw:required · **Milestone:** Wave 1 · **Depends on:** T00

## Goal
Make the app build reproducibly on a current JDK (17 or 21) with the Gradle wrapper, producing an APK that still loads on Android 2.3.7 Dalvik (API 10).

## Context
- The current build uses AGP 3.5.0, Gradle 5.x, `compileSdkVersion 10`, Java 1.6 and `jcenter()`, plus `com.github.ma1co.OpenMemories-Framework:{framework,stubs}:-SNAPSHOT` from jitpack. None of that runs on JDK 17+.
- `app/build.gradle.new` is a stale leftover from upstream. Delete it.
- Target: AGP 8.x and Gradle 8.x, with `compileSdk 34`, `minSdk 10`, `targetSdk 10`, Java source/target 8, and **no** core-library desugaring. Set `namespace` to the current Java package `com.jonasjuffinger.timelapse` so that T03 can rename it.

## Scope / files
`build.gradle`, `settings.gradle`, `app/build.gradle`, `gradle/wrapper/*`, `gradlew*`, `app/proguard-rules.pro`, `gradle.properties` (new), and the AGENTS.md § Commands section.

## Tasks
1. Pin OpenMemories-Framework by **commit hash** on jitpack instead of `-SNAPSHOT`. If jitpack can't build it, vendor the `framework` and `stubs` jars into `app/libs/` (stubs stay `compileOnly`). Confirm the framework's license and record it in README credits.
2. Replace `jcenter()` with `google()` + `mavenCentral()` (+ jitpack).
3. Make R8/ProGuard keep the Sony callback interfaces and anything reached by reflection. Check that the release build still runs.
4. Check that AGP 8 is happy with `minSdk 10` (it warns, but D8 supports min-api 10). If D8 output fails to verify on the camera, fall back to AGP 7.4 + Gradle 7.6 on a JDK 17 and document why.
5. Update AGENTS.md § Commands with the exact build and lint commands.

## Acceptance
- [ ] `./gradlew :app:assembleDebug :app:assembleRelease :app:lint` passes from a clean clone on JDK 17 or 21.
- [ ] `aapt dump badging` (or `apkanalyzer manifest min-sdk`) on the APK shows `sdkVersion:'10'`, `targetSdkVersion:'10'` and package `io.github.nathanrcast.timelapse`.
- [ ] Only `assets/`, `res/`, `classes.dex` and the manifest are in the APK. No `kotlin/` and no `META-INF` version files that bloat it.
- [ ] **HW:** debug APK installs and launches on the a6500 *and* the NEX-5T; a 10-shot run completes; `TIMELPS/LOG.TXT` attached (pending maintainer).

## Verify
`./gradlew clean :app:assembleDebug :app:lint`, then `$ANDROID_HOME/build-tools/<ver>/aapt dump badging app/build/outputs/apk/debug/*.apk | head -3`
