# T17: Desktop assembly + user docs

**Labels:** type:docs area:tools size:S · **Milestone:** Wave 4 · **Depends on:** T16

## Tasks
- `tools/assemble.sh <dir> [--fps 24] [--deflicker] [--crop 16:9] [--dry-run]`: builds an ffmpeg MP4 from the JPEG sequence, with an optional `deflicker` filter. When there are several runs it uses LOG.CSV `run_id` to group files.
- README:
  - rewrite Usage for the new UI
  - camera compatibility matrix (from `docs/cameras.md`)
  - troubleshooting (LENR, drive mode, silent shutter)
- Finalize `docs/hw_test_checklist.md`.

## Acceptance
- [ ] `tools/assemble.sh tests/fixtures/jpegs --dry-run` prints the ffmpeg command.
- [ ] A real run on fixture JPEGs produces a playable MP4 (CI can install ffmpeg).
