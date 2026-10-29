#!/usr/bin/env bash
# Create the GitHub repo, push main, and seed labels, milestones and one issue per docs/tickets/T##-*.md.
# Idempotent: existing repo, labels, milestones and issues (matched by title) are left alone.
set -euo pipefail

REPO="nathanrcast/pmca-timelapse"
DESC="TimeLapse+: in-camera timelapse app for Sony PlayMemories cameras (a6500, NEX-5T) — maintained fork of jonasjuffinger/TimeLapse"
VIS="--public"
DRY=0
for a in "$@"; do
  case "$a" in
    --public) VIS="--public" ;;
    --private) VIS="--private" ;;
    --dry-run) DRY=1 ;;
    *) echo "usage: $0 [--public|--private] [--dry-run]" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")/.."
run() { if [ "$DRY" = 1 ]; then printf '+'; printf ' %q' "$@"; echo; else "$@"; fi; }

if gh repo view "$REPO" >/dev/null 2>&1; then
  echo "repo $REPO exists"
else
  run gh repo create "$REPO" "$VIS" --description "$DESC"
fi
run git push -u origin main

while read -r name color desc; do
  run gh label create "$name" --repo "$REPO" --color "$color" --description "$desc" --force
done <<'LABELS'
type:spike 5319e7 Time-boxed investigation
type:feature 0e8a16 New capability
type:bug d73a4a Something broken
type:refactor c5def5 Restructure without behavior change
type:infra bfdadc Build, CI, release
type:docs 0075ca Documentation
area:build ededed Gradle, CI, signing
area:core ededed Pure-Java :core module
area:camera ededed Sony camera API and capability layer
area:ui ededed Activities, input, HUD
area:tools ededed Desktop scripts and log tools
size:S fef2c0 Under half a day
size:M fbca04 About a day
size:L e99695 Multi-day
hw:required b60205 Acceptance needs the maintainer to run it on a camera
LABELS

existing_ms=""
[ "$DRY" = 1 ] || existing_ms=$(gh api "repos/$REPO/milestones?state=all&per_page=100" --jq '.[].title' 2>/dev/null || true)
for w in 0 1 2 3 4; do
  grep -qx "Wave $w" <<<"$existing_ms" || run gh api "repos/$REPO/milestones" -f title="Wave $w" --silent
done

existing_issues=""
[ "$DRY" = 1 ] || existing_issues=$(gh issue list --repo "$REPO" --state all --limit 500 --json title --jq '.[].title')
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
for f in docs/tickets/T*.md; do
  title=$(head -1 "$f" | sed 's/^# //')
  if grep -qxF "$title" <<<"$existing_issues"; then echo "issue exists: $title"; continue; fi
  meta=$(grep -m1 '^\*\*Labels:\*\*' "$f")
  labels=$(sed -E 's/^\*\*Labels:\*\* ([^·]*) ·.*/\1/' <<<"$meta" | xargs | tr ' ' ',')
  milestone=$(sed -E 's/.*\*\*Milestone:\*\* ([^·]*) ·.*/\1/' <<<"$meta" | xargs)
  body="$tmp/$(basename "$f")"
  { tail -n +2 "$f"; printf '\n---\nSpec: `%s`\n' "$f"; } > "$body"
  if [ "$DRY" = 1 ]; then
    run gh issue create --repo "$REPO" --title "$title" --label "$labels" --milestone "$milestone" --body-file "$f"
    continue
  fi
  url=$(gh issue create --repo "$REPO" --title "$title" --label "$labels" --milestone "$milestone" --body-file "$body")
  echo "$title -> $url"
  case "$title" in T00:*) gh issue close "$url" --reason completed --comment "Done in the bootstrap commit." >/dev/null ;; esac
done
