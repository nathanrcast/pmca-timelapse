# T04: Port the 2026 fork fixes

**Labels:** type:bug area:ui size:M hw:required · **Milestone:** Wave 1 · **Depends on:** T03

## Goal
Pick up the small, clearly beneficial fixes from two 2026 forks, one commit per fix, with authorship preserved via `git cherry-pick -x`, or `Co-authored-by` if a pick had to be rewritten.

## Candidates
- JEF1056/TimeLapse (2026-08):
  - press-and-hold rapid slider increments
  - settings UI focus step and time formatting
  - "Fix display timing, prevent black screen flashes, and add delay countdown"
- Zyphoriate/TimeLapse v1.5.0 (2026-06): a chain of DOFF/review-freeze commits, ending in "Revert to working state". Work out the net diff first and port only the net fix.
- Skip themetalleg/TimeLapse-v2, which has no shared history and only icon/tasks changes.

## Tasks
1. `git remote add jef https://github.com/JEF1056/TimeLapse.git`, `git remote add zyph https://github.com/Zyphoriate/TimeLapse.git`, then fetch both.
2. For each candidate, read the diff, decide take or skip, and record the decision with a one-line reason in the PR description.
3. Resolve them against the T03 namespace.

## Acceptance
- [ ] Each ported fix is its own commit referencing the source commit.
- [ ] The PR lists every candidate as taken or skipped, with its reason.
- [ ] Build and lint pass.
- [ ] **HW:** press-and-hold works on both bodies; with DOFF on, there's no black flash between shots on the a6500 (pending maintainer).
