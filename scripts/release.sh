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
unsigned="TimeLapsePlus-$version-unsigned.apk"
aligned="TimeLapsePlus-$version-aligned.apk"
final="TimeLapsePlus-$version.apk"

step() { echo "==> $*"; }
run() { if [ "$dry" -eq 1 ]; then echo "[dry-run] $*"; else "$@"; fi; }

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f local.properties ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
bt=""
if [ -n "$sdk" ] && [ -d "$sdk/build-tools" ]; then
  bt="$sdk/build-tools/$(ls "$sdk/build-tools" | sort -V | tail -1)"
fi
if [ -z "$bt" ] || [ ! -x "$bt/apksigner" ]; then
  if [ "$dry" -eq 1 ]; then bt="${bt:-<ANDROID_HOME>/build-tools/<newest>}"
  else echo "apksigner not found; set ANDROID_HOME" >&2; exit 1; fi
fi
step "build-tools: $bt"

props=keystore.properties
prop() { sed -n "s/^$1=//p" "$props" | head -1; }
if [ ! -f "$props" ]; then
  [ "$dry" -eq 1 ] && echo "note: $props missing (required for a real run)" \
    || { echo "$props missing; copy keystore.properties.example" >&2; exit 1; }
fi

if [ "$local_build" -eq 1 ]; then
  step "build unsigned APK locally"
  run ./gradlew :app:assembleRelease
  run cp app/build/outputs/apk/release/app-release-unsigned.apk "$unsigned"
else
  step "download $unsigned from draft release $tag"
  run gh release download "$tag" --pattern "$unsigned" --clobber
fi

step "zipalign"
if [ "$dry" -eq 1 ]; then
  echo "[dry-run] zipalign -c -p 4 $unsigned || zipalign -p 4 $unsigned $aligned"
else
  if "$bt/zipalign" -c -p 4 "$unsigned"; then cp "$unsigned" "$aligned"
  else "$bt/zipalign" -f -p 4 "$unsigned" "$aligned"; fi
fi

step "sign (v1+v2)"
if [ "$dry" -eq 1 ]; then
  echo "[dry-run] apksigner sign --ks <storeFile> --ks-key-alias <keyAlias> --ks-pass env:KS_PASS --key-pass env:KEY_PASS --v1-signing-enabled true --v2-signing-enabled true --out $final $aligned"
else
  KS_PASS="$(prop storePassword)" KEY_PASS="$(prop keyPassword)" \
  "$bt/apksigner" sign --ks "$(prop storeFile)" --ks-key-alias "$(prop keyAlias)" \
    --ks-pass env:KS_PASS --key-pass env:KEY_PASS \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out "$final" "$aligned"
fi

step "verify"
if [ "$dry" -eq 1 ]; then echo "[dry-run] apksigner verify --print-certs $final"
else "$bt/apksigner" verify --print-certs "$final"; fi

step "checksum"
if [ "$dry" -eq 1 ]; then echo "[dry-run] sha256sum $final > $final.sha256"
else sha256sum "$final" > "$final.sha256"; cat "$final.sha256"; fi

step "upload to draft release $tag"
run gh release upload "$tag" "$final" "$final.sha256" --clobber
[ "$dry" -eq 1 ] && echo "dry run complete; nothing changed"
exit 0
