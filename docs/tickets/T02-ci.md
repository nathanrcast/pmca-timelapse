# T02: CI + local release script

**Labels:** type:infra area:build size:S · **Milestone:** Wave 1 · **Depends on:** T01

## Goal
Every PR gets an automatic build/lint check, tags produce an unsigned release APK, and signing stays on the maintainer's machine.

## Scope / files
`.github/workflows/ci.yml`, `.github/workflows/release.yml`, `scripts/release.sh`, and AGENTS.md § Commands.

## Tasks
1. `ci.yml` runs on `pull_request` and on pushes to `main`. It uses Temurin JDK (matching T01) with the Gradle cache, runs `./gradlew :app:assembleDebug :app:lint` (plus `:core:test` once that module exists, guarded with `if [ -d core ]`), and uploads the debug APK and lint report as artifacts. Name the job `build` so branch protection can require it.
2. `release.yml` runs on tag `v*`. It builds `assembleRelease` unsigned and attaches the APK to a **draft** GitHub release.
3. `scripts/release.sh <version>` (bash with `--dry-run`):
   - downloads the draft release's unsigned APK, or builds it locally
   - signs it with `apksigner`, reading the keystore path and passwords from a gitignored `keystore.properties`
   - verifies it with `apksigner verify --print-certs`
   - writes `sha256sum`
   - uploads it to the draft release with `gh release upload`
   
   It must never print passwords.
4. After this merges, the orchestrator turns on branch protection on `main`: require PRs, require the `build` status check, no required approvals.

## Acceptance
- [ ] The PR for this ticket shows a green `build` check with the APK artifact attached.
- [ ] `scripts/release.sh 0.0.0-test --dry-run` prints the steps without signing or uploading.
- [ ] No secrets or keystore files are referenced in the workflows.
