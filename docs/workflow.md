# Ticket → PR → review → merge

The maintainer runs one orchestrating agent session. It hands tickets to implementer agents and has a separate reviewer agent check each PR.

## Board

- One GitHub issue per `docs/tickets/T##-*.md`. Its title is `T##: <title>` and the ticket file is the source of truth. If scope changes, edit the file in the PR and the issue body together.
- Labels: `type:{spike,feature,bug,refactor,infra,docs}`, `area:{build,core,camera,ui,tools}`, `size:{S,M,L}`, `hw:required`.
- Milestones: `Wave 0` … `Wave 4`.

## Per ticket

1. **Branch.** Name it `t##-<slug>` off `main`, with one worktree per ticket. Parallel tickets in a wave must touch disjoint files; the orchestrator checks the "Files" section of each ticket before launching them together.
2. **Implement.** Keep to the ticket scope and run the AGENTS.md pre-push gate. Commits are small and imperative ("Add drift-free scheduler"), with no AI attribution trailers.
3. **PR.** Open it with `gh pr create --base main --title "T##: <title>"`. The body holds `Closes #<issue>` (`Refs #<issue>` for `hw:required` tickets, see the hardware deferral below) and the filled PR template. Tick acceptance boxes only for things actually verified, and list hardware boxes as **pending maintainer**.
4. **Review.** A reviewer agent posts inline comments on the PR. The implementer addresses them by pushing new commits (no force-push during review). Escalate to the maintainer after two rounds with findings still open.
5. **Merge gate.** A PR merges when:
   - CI is green
   - review findings are resolved or explicitly waived in a PR comment

   **Hardware deferral:** while the maintainer can't run camera sessions (adopted 2026-10-29 so software work isn't blocked on camera availability; hardware evidence is still required before the release, T18), `hw:required` PRs merge once CI and review pass. The PR body uses `Refs #N` instead of `Closes #N`, so the issue stays open with its on-camera boxes. A comment on the issue says what is pending, and all deferred checks run in one consolidated camera session before the release (T18).

   Squash-merge and delete the branch, except PRs that import external history (T03, T04): those use a merge commit so upstream authorship and `git log --follow` survive. Rebase any open sibling branches onto the new `main`.
6. **Close-out.** Update the ticket file's status line and `docs/plan.md` if the scope moved, in the same PR.

## Wave cadence

- Up to 3 implementers run in parallel. PRs merge in dependency order.
- A wave ends with one **camera session**: the maintainer installs the merged `main` build on both bodies and runs `docs/hw_test_checklist.md`. Results go onto the open `hw:required` issues and PRs.
- The next wave starts once the previous wave's PRs have merged. Hardware boxes are deferred per the hardware-deferral rule above.

## Branch protection (`main`)

- Changes go in by PR only, and the CI check (from T02 on) is required.
- Required approvals are off. The agents act as the maintainer's own account, and GitHub doesn't let an account approve its own PR. The review step is the reviewer agent's comments plus the merge gate above.
