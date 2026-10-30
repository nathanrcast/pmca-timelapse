#!/usr/bin/env bash
# Samples the app's memory on a camera over Tweak Wi-Fi ADB while a soak runs.
# Usage: tools/soak_meminfo.sh <camera-ip[:port]> [interval-min=30] [--dry-run]
# adb comes from $ADB, else PATH. Appends to soak-meminfo.log (override with $SOAK_LOG); stop with Ctrl-C.
# Analyse with: python3 -I tools/meminfo_trend.py soak-meminfo.log
set -u

PKG=io.github.nathanrcast.timelapse
LOG=${SOAK_LOG:-soak-meminfo.log}
ADB=${ADB:-adb}
dry=0
args=()
for a in "$@"; do
  case "$a" in
    --dry-run) dry=1 ;;
    -h|--help) sed -n 2,5p "$0"; exit 0 ;;
    *) args+=("$a") ;;
  esac
done

if [ "${#args[@]}" -lt 1 ] || [ "${#args[@]}" -gt 2 ]; then
  echo "usage: $0 <camera-ip[:port]> [interval-min] [--dry-run]" >&2
  exit 2
fi
target=${args[0]}
interval=${args[1]:-30}
case "$interval" in
  ''|*[!0-9]*|0) echo "interval-min must be a positive integer" >&2; exit 2 ;;
esac
case "$target" in
  *:*) ;;
  *) target=$target:5555 ;;
esac

if [ "$dry" -eq 1 ]; then
  echo "would append to $LOG every $interval min:"
  echo "  $ADB connect $target"
  echo "  $ADB -s $target shell dumpsys meminfo $PKG"
  exit 0
fi

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "adb not found: set \$ADB or put it on PATH" >&2
  exit 2
fi

trap 'echo "stopped"; exit 0' INT TERM
while true; do
  stamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  "$ADB" connect "$target" >/dev/null 2>&1
  out=$("$ADB" -s "$target" shell dumpsys meminfo "$PKG" 2>&1 | tr -d '\r')
  {
    echo "=== $stamp ==="
    echo "$out"
  } >> "$LOG"
  echo "$stamp sampled ($(printf '%s\n' "$out" | wc -l) lines)"
  sleep "$((interval * 60))"
done
