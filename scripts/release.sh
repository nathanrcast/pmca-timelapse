#!/usr/bin/env bash
set -euo pipefail

usage() { echo "usage: $0 <version> [--local] [--dry-run]" >&2; exit 2; }

version=""; local_build=0; dry=0
for a in "$@"; do
  case "$a" in
    --dry-run) dry=1 ;;
    --local) local_build=1 ;;
    -*) usage ;;
    *) [ -z "$version" ] && version="$a" || usage ;;
  esac
done
[ -n "$version" ] || usage
version="${version#v}"

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"
tag="v$version"
# Asset name pattern; .github/workflows/release.yml builds the same -unsigned name.
unsigned="TimeLapsePlus-$version-unsigned.apk"
aligned="TimeLapsePlus-$version-aligned.apk"
final="TimeLapsePlus-$version.apk"

step() { echo "==> $*"; }
run() {
  if [ "$dry" -eq 1 ]; then printf '[dry-run]'; printf ' %q' "$@"; echo; else "$@"; fi
}
# Abort for real runs; in dry-run report what would abort and continue.
fail() {
  if [ "$dry" -eq 1 ]; then echo "[dry-run] would abort: $*"; else echo "error: $*" >&2; exit 1; fi
}

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f local.properties ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' local.properties | tr -d '\r' | head -1)"
fi
bt=""
if [ -n "$sdk" ] && [ -d "$sdk/build-tools" ]; then
  while IFS= read -r d; do
    if [ -x "$sdk/build-tools/$d/apksigner" ] && [ -x "$sdk/build-tools/$d/zipalign" ]; then bt="$sdk/build-tools/$d"; break; fi
  done < <(ls "$sdk/build-tools" | sort -Vr)
fi
if [ -z "$bt" ]; then
  fail "no build-tools with apksigner and zipalign under \$ANDROID_HOME"
  bt="<ANDROID_HOME>/build-tools/<newest>"
fi
step "build-tools: $bt"

gradle_version="$(sed -n "s/^[[:space:]]*versionName[[:space:]]*['\"]\([^'\"]*\)['\"].*/\1/p" app/build.gradle | head -1)"
[ "$gradle_version" = "$version" ] || fail "versionName '$gradle_version' in app/build.gradle does not match $version"

props=keystore.properties
prop() {
  local v
  v="$(tr -d '\r' < "$props" | sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" | head -1 | sed 's/[[:space:]]*$//')"
  case "$v" in "~/"*) v="$HOME/${v#\~/}" ;; esac
  printf '%s' "$v"
}
if [ ! -f "$props" ]; then
  fail "$props missing; copy keystore.properties.example"
fi

if [ "$local_build" -eq 1 ]; then
  step "build unsigned APK locally"
  if ! git diff --quiet || ! git diff --cached --quiet; then fail "--local needs a clean working tree"; fi
  tag_commit="$(git rev-parse -q --verify "$tag^{commit}" || true)"
  [ -n "$tag_commit" ] && [ "$(git rev-parse HEAD)" = "$tag_commit" ] || fail "--local needs HEAD checked out at tag $tag"
  run ./gradlew :app:assembleRelease
  run cp app/build/outputs/apk/release/app-release-unsigned.apk "$unsigned"
else
  step "download $unsigned from draft release $tag"
  run gh release download "$tag" --pattern "$unsigned" --clobber
fi

step "zipalign"
if [ "$dry" -eq 0 ] && "$bt/zipalign" -c -p 4 "$unsigned"; then
  run cp "$unsigned" "$aligned"
else
  [ "$dry" -eq 1 ] && run "$bt/zipalign" -c -p 4 "$unsigned"
  run "$bt/zipalign" -f -p 4 "$unsigned" "$aligned"
fi

step "sign (v1+v2)"
if [ -f "$props" ]; then
  KS_PASS="$(prop storePassword)"; KEY_PASS="$(prop keyPassword)"
  ks_file="$(prop storeFile)"; ks_alias="$(prop keyAlias)"
else
  KS_PASS=""; KEY_PASS=""; ks_file="<storeFile>"; ks_alias="<keyAlias>"
fi
export KS_PASS KEY_PASS
run "$bt/apksigner" sign --ks "$ks_file" --ks-key-alias "$ks_alias" \
  --ks-pass env:KS_PASS --key-pass env:KEY_PASS \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$final" "$aligned"

step "verify"
run "$bt/apksigner" verify --print-certs "$final"

step "checksum"
if [ "$dry" -eq 1 ]; then echo "[dry-run] sha256sum $final > $final.sha256"
else sha256sum "$final" > "$final.sha256"; cat "$final.sha256"; fi

step "upload to draft release $tag"
run gh release upload "$tag" "$final" "$final.sha256" --clobber
run gh release delete-asset "$tag" "$unsigned" --yes
[ "$dry" -eq 1 ] && echo "dry run complete; nothing changed"
exit 0
