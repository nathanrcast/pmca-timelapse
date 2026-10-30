#!/usr/bin/env bash
# Build an H.264 MP4 from a TimeLapse+ JPEG sequence. Needs ffmpeg at run time (not for --dry-run).
set -euo pipefail

usage() {
  echo "usage: $0 <dir> [--fps 24] [--deflicker] [--crop 16:9] [--out file.mp4] [--run <run_id>] [--dry-run]" >&2
  exit 2
}

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
dir="" fps=24 deflicker=0 crop="" out="timelapse.mp4" run="" dry=0

while [ $# -gt 0 ]; do
  case "$1" in
    --fps) [ $# -ge 2 ] || usage; fps="$2"; shift 2 ;;
    --deflicker) deflicker=1; shift ;;
    --crop) [ $# -ge 2 ] || usage; crop="$2"; shift 2 ;;
    --out) [ $# -ge 2 ] || usage; out="$2"; shift 2 ;;
    --run) [ $# -ge 2 ] || usage; run="$2"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    -h|--help) usage ;;
    -*) echo "unknown option: $1" >&2; usage ;;
    *) [ -z "$dir" ] || usage; dir="$1"; shift ;;
  esac
done

[ -n "$dir" ] || usage
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
for c in "$dir/LOG.CSV" "$dir/TIMELPS/LOG.CSV" "$dir/../TIMELPS/LOG.CSV"; do
  if [ -f "$c" ]; then log="$c"; break; fi
done

helper=(python3 -I "$here/frames_for_run.py" "$dir")
if [ -n "$run" ]; then
  [ -n "$log" ] || { echo "--run needs LOG.CSV in <dir> or <dir>/../TIMELPS/" >&2; exit 2; }
  helper+=(--log "$log" --run "$run")
elif [ -n "$log" ]; then
  runs="$("${helper[@]}" --log "$log" --list-runs | paste -sd' ' -)"
  case "$runs" in *" "*) echo "note: $log holds several runs and all frames are used; pick one with --run (ids: $runs)" >&2 ;; esac
fi
rc=0
raw="$("${helper[@]}")" || rc=$?
[ "$rc" -eq 0 ] || [ "$rc" -eq 3 ] || exit "$rc"
frames=()
[ -z "$raw" ] || mapfile -t frames <<<"$raw"
count="${#frames[@]}"
if [ "$count" -eq 0 ]; then
  echo "no JPEG frames found${run:+ for run $run} in $dir" >&2
  exit 1
fi

list="$(mktemp "${TMPDIR:-/tmp}/assemble.XXXXXX")"
trap 'rm -f "$list"' EXIT
frame_s="$(awk -v f="$fps" 'BEGIN { printf "%.6f", 1 / f }')"
for f in "${frames[@]}"; do
  abs="$(cd "$(dirname "$f")" && pwd)/$(basename "$f")"
  printf "file '%s'\nduration %s\n" "${abs//\'/\'\\\'\'}" "$frame_s"
done > "$list"
last="$(cd "$(dirname "${frames[$((count-1))]}")" && pwd)/$(basename "${frames[$((count-1))]}")"
printf "file '%s'\n" "${last//\'/\'\\\'\'}" >> "$list"

filters=()
if [ -n "$crop" ]; then
  filters+=("crop=w='trunc(min(iw,ih*$cw/$ch)/2)*2':h='trunc(min(ih,iw*$ch/$cw)/2)*2'")
fi
[ "$deflicker" -eq 1 ] && filters+=("deflicker=size=5")

cmd=(ffmpeg -y -f concat -safe 0 -i "$list")
if [ "${#filters[@]}" -gt 0 ]; then
  vf="$(IFS=,; echo "${filters[*]}")"
  cmd+=(-vf "$vf")
fi
cmd+=(-r "$fps" -c:v libx264 -crf 18 -pix_fmt yuv420p -movflags +faststart "$out")

echo "frames: $count (${fps} fps = $(awk -v n="$count" -v f="$fps" 'BEGIN { printf "%.1f", n / f }') s)"
printf '%q ' "${cmd[@]}"; echo
if [ "$dry" -eq 1 ]; then
  echo "dry run: nothing executed"
  exit 0
fi
"${cmd[@]}"
echo "wrote $out"
