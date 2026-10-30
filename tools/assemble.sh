#!/usr/bin/env bash
# Build an H.264 MP4 from a TimeLapse+ JPEG sequence. Needs ffmpeg at run time (not for --dry-run).
# Kept compatible with bash 3.2 (macOS).
set -euo pipefail

usage() {
  cat <<'USAGE'
usage: assemble.sh <dir> [--fps 24] [--deflicker] [--crop 16:9] [--out file.mp4] [--run <run_id>]
                   [--offset-s N] [--window-s N] [--dry-run]

  <dir>         DCIM folder (or a copy); every .JPG/.JPEG under it is used, ordered by folder then name
  --run ID      keep only that run's frames, matched by file modification time against LOG.CSV
                (found in <dir>, <dir>/TIMELPS, or up to two levels above <dir> in TIMELPS/)
  --offset-s N  shift log times by N seconds (clock or time-zone mismatch); --window-s N minimum slack (default 2)
  --dry-run     print the ffmpeg command and frame count, run nothing

Copy the card with timestamps preserved (cp -rp, rsync -rt), or --run cannot match frames to the log.
USAGE
}

fail_usage() { usage >&2; exit 2; }

here="$(cd "$(dirname "$0")" && pwd)"
dir="" fps=24 deflicker=0 crop="" out="timelapse.mp4" run="" dry=0 offset="" window=""

while [ $# -gt 0 ]; do
  case "$1" in
    --fps) [ $# -ge 2 ] || fail_usage; fps="$2"; shift 2 ;;
    --deflicker) deflicker=1; shift ;;
    --crop) [ $# -ge 2 ] || fail_usage; crop="$2"; shift 2 ;;
    --out) [ $# -ge 2 ] || fail_usage; out="$2"; shift 2 ;;
    --run) [ $# -ge 2 ] || fail_usage; run="$2"; shift 2 ;;
    --offset-s) [ $# -ge 2 ] || fail_usage; offset="$2"; shift 2 ;;
    --window-s) [ $# -ge 2 ] || fail_usage; window="$2"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "unknown option: $1" >&2; fail_usage ;;
    *) [ -z "$dir" ] || fail_usage; dir="$1"; shift ;;
  esac
done

[ -n "$dir" ] || fail_usage
[ -d "$dir" ] || { echo "not a directory: $dir" >&2; exit 2; }
[[ "$fps" =~ ^[0-9]+$ ]] && [ "$fps" -gt 0 ] || { echo "--fps must be a positive integer" >&2; exit 2; }
if [ -n "$crop" ]; then
  [[ "$crop" =~ ^([0-9]+):([0-9]+)$ ]] || { echo "--crop must look like 16:9" >&2; exit 2; }
  cw="${BASH_REMATCH[1]}" ch="${BASH_REMATCH[2]}"
fi
if [ "$dry" -eq 0 ] && ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg not found. Install it (Debian/Ubuntu: sudo apt install ffmpeg; macOS: brew install ffmpeg; Windows: winget install ffmpeg) or use --dry-run." >&2
  exit 127
fi
command -v python3 >/dev/null 2>&1 || { echo "python3 not found" >&2; exit 127; }

log=""
for c in "$dir/LOG.CSV" "$dir/TIMELPS/LOG.CSV" "$dir/../TIMELPS/LOG.CSV" "$dir/../../TIMELPS/LOG.CSV"; do
  if [ -f "$c" ]; then log="$c"; break; fi
done

helper=(python3 -I "$here/frames_for_run.py" "$dir")
[ -z "$offset" ] || helper+=(--offset-s "$offset")
[ -z "$window" ] || helper+=(--window-s "$window")
if [ -n "$run" ]; then
  [ -n "$log" ] || { echo "--run needs LOG.CSV in <dir> or in TIMELPS/ next to DCIM" >&2; exit 2; }
  helper+=(--log "$log" --run "$run")
elif [ -n "$log" ]; then
  if runs="$(python3 -I "$here/frames_for_run.py" "$dir" --log "$log" --list-runs 2>/dev/null)"; then
    runs="$(echo "$runs" | paste -sd' ' -)"
    case "$runs" in *" "*) echo "note: $log holds several runs and all frames are used; pick one with --run (ids: $runs)" >&2 ;; esac
  else
    echo "warning: could not read $log; using all frames" >&2
  fi
fi

frames="$(mktemp "${TMPDIR:-/tmp}/assemble.XXXXXX")"
list="$(mktemp "${TMPDIR:-/tmp}/assemble.XXXXXX")"
trap 'rm -f "$frames" "$list"' EXIT
rc=0
"${helper[@]}" > "$frames" || rc=$?
[ "$rc" -eq 0 ] || [ "$rc" -eq 3 ] || exit "$rc"
count="$(grep -c . "$frames" || true)"
if [ "$count" -eq 0 ]; then
  echo "no JPEG frames found${run:+ for run $run} in $dir" >&2
  exit 1
fi

sed "s/'/'\\\\''/g" "$frames" | awk -v d="$(awk -v f="$fps" 'BEGIN { printf "%.6f", 1 / f }')" '
  NF { printf "file \047%s\047\nduration %s\n", $0, d; last = $0 }
  END { printf "file \047%s\047\n", last }' > "$list"

filters=""
if [ -n "$crop" ]; then
  filters="crop=w='trunc(min(iw,ih*$cw/$ch)/2)*2':h='trunc(min(ih,iw*$ch/$cw)/2)*2'"
fi
if [ "$deflicker" -eq 1 ]; then
  filters="${filters:+$filters,}deflicker=size=5"
fi

cmd=(ffmpeg -y -f concat -safe 0 -i "$list")
if [ -n "$filters" ]; then
  cmd+=(-vf "$filters")
fi
cmd+=(-r "$fps" -c:v libx264 -crf 18 -pix_fmt yuv420p -movflags +faststart "$out")

echo "frames: $count ($fps fps = $(awk -v n="$count" -v f="$fps" 'BEGIN { printf "%.1f", n / f }') s)"
printf '%q ' "${cmd[@]}"; echo
if [ "$dry" -eq 1 ]; then
  echo "dry run: nothing executed"
  exit 0
fi
"${cmd[@]}"
echo "wrote $out"
