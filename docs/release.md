# Releasing 2.0.0

Maintainer steps, in order. The release signing key is permanent (`AGENTS.md`): installers pin its certificate on first install. CI never signs; signing is local.

## 1. Camera session and release gate

Run the consolidated session in `docs/hw_test_checklist.md` on both bodies, using the build from merged `main`, including the soak. Post results on the open `hw:required` issues and confirm the release gate passed (analyzer drift < 100 ms, 0 unexplained skips, camera responsive afterwards). Do not continue on a failed gate.

## 2. Create the keystore (once)

```
scripts/make-keystore.sh --dry-run
scripts/make-keystore.sh
```

Writes `~/keystores/pmca-timelapse-release.jks`, a random password in `~/keystores/pmca-timelapse-release.cred` (mode 600) and the gitignored `keystore.properties`. It refuses to overwrite an existing keystore. Flags `--keystore`, `--cred`, `--props`, `--alias` and `--dname` override the defaults.

**Back up the `.jks` and `.cred` offline now.** Losing either forces every user to uninstall before updating. Never put them in the repo or in CI secrets.

## 3. Tag (creates the draft release)

On an up-to-date `main` whose `app/build.gradle` has `versionName '2.0.0'`:

```
git checkout main && git pull --ff-only
git tag v2.0.0 && git push origin v2.0.0
```

`.github/workflows/release.yml` builds the unsigned APK and creates a draft release with `TimeLapsePlus-2.0.0-unsigned.apk`. Wait for the run to finish.

## 4. Sign and upload

```
scripts/release.sh 2.0.0 --dry-run
scripts/release.sh 2.0.0
```

Downloads the unsigned asset, zipaligns, signs (v1+v2), verifies, writes the checksum, uploads `TimeLapsePlus-2.0.0.apk` and its `.sha256` to the draft, and removes the unsigned asset. Check the printed certificate is the release cert, not a debug one. `--local` builds from a clean checkout at the tag instead of downloading.

## 5. Publish

In the GitHub UI: Releases, open the `v2.0.0` draft, paste the 2.0.0 section of `CHANGELOG.md` as the notes, Publish. Then close issue 19.

## 6. Test a fresh install from the source

Install through PMCA-RE RELOADED from the source, not from a local file, with the camera connected over USB:

```
pmca-console sources add nathanrcast/pmca-timelapse
pmca-console apps
pmca-console install --app io.github.nathanrcast.timelapse --source nathanrcast/pmca-timelapse
```

`apps` must list TimeLapse+ with version 2.0.0. `install --app` resolves the newest release asset through `pmca-source.yaml`, so this fails if the file or the release is wrong. Confirm the app launches on both bodies. In the PMCA-RE RELOADED app the same flow is Settings, App and plugin sources, add the repository, then pick TimeLapse+ on the Apps page.

## 7. Optional: list it in the OpenMemories app list

PMCA-RE RELOADED finds sources by the GitHub topic `pmca-source`. Add it (the topic is not set by the release prep):

```
gh repo edit nathanrcast/pmca-timelapse --add-topic pmca-source
```

To also appear in `ma1co/OpenMemories-AppList`, open a PR there appending the entry from `pmca-source.yaml` to `apps.yaml`. This is outward-facing, so it is your call.
