# T18: Release 2.0.0

**Status:** release prep merged (version, CHANGELOG, `pmca-source.yaml`, `scripts/make-keystore.sh`, `docs/release.md`); publish steps pending the maintainer after the camera session.

**Labels:** type:infra area:build size:S hw:required · **Milestone:** Wave 4 · **Depends on:** T15, T17

## Tasks
1. Bump to `versionName "2.0.0"` and raise `versionCode` above upstream's 7, then write the CHANGELOG.
2. The maintainer creates the release keystore. It is **permanent**: back it up offline before the first public release.
3. Tag `v2.0.0`, let the release workflow draft the release, then run `scripts/release.sh 2.0.0` locally to sign and upload.
4. Add `pmca-source.yaml` at the repo root in the OpenMemories app-list format, so PMCA-RE RELOADED can install from this repo:
   - package `io.github.nathanrcast.timelapse`
   - name `TimeLapse+`
   - `release: {type: github, user: nathanrcast, repo: pmca-timelapse}`
5. Optional: open a PR adding the app to `ma1co/OpenMemories-AppList`. This is outward-facing, so the maintainer decides.

## Acceptance
- [ ] `apksigner verify --print-certs` on the release APK shows the release cert.
- [ ] **HW:** a fresh install through PMCA-RE RELOADED from the source works on both bodies (pending maintainer).
