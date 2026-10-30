# T05: Extract a pure-Java `:core` module

**Labels:** type:refactor area:core size:M · **Milestone:** Wave 2 · **Depends on:** T02, T03

## Goal
Pull the logic that needs no camera into a JVM-testable `java-library` module so that scheduler, ramp and plan math can be unit-tested in CI.

## Scope / files
`settings.gradle`, `core/build.gradle`, `core/src/main/java/io/github/nathanrcast/timelapse/core/**`, `core/src/test/java/**`, `app/build.gradle` (`implementation project(':core')`), call sites in `SettingsActivity`/`ShootActivity`, `.github/workflows/ci.yml` and `AGENTS.md`.

## Tasks
1. Create `:core` with Java 8 source/target, no Android deps, and JUnit 4.
2. Move in:
   - `ShotPlan`: total duration, clip length at N fps, end time, shots remaining. This is the math currently inline in `SettingsActivity`/`ShootActivity`.
   - The `Settings` value model. Persistence goes behind an interface (`SettingsStore`) that the app implements with SharedPreferences.
   - `Util` (formatters, step tables) and `SliderMapping`, with their tests.
3. Add a `Clock` interface (`long nowMs()`) for T06 and T14 to use.
4. Run `:core:check` (`:core:test` plus a JaCoCo rule on `ShotPlan`) unconditionally in CI, replacing T02's `if [ -d core ]` guard.

## Acceptance
- [ ] `./gradlew :core:test` passes, with ≥ 90 % line coverage on `ShotPlan`.
- [ ] `:core` has no `android.*` or `com.sony.*` imports (check with grep in the test or CI).
- [ ] App behavior is unchanged: same settings and same displayed durations.
