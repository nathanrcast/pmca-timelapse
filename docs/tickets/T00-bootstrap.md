# T00: Bootstrap repo

**Labels:** type:infra area:build size:M · **Milestone:** Wave 0 · **Depends on:** — · **Status:** done in the initial commit

## Goal
Stand up the hard fork as a standalone repo with upstream history, a distinct app id, agent docs, the ticket board and PR templates.

## Done
- [x] Upstream history imported. `main` is the default branch, with remotes `upstream` (jonasjuffinger/TimeLapse) and `frosbe`.
- [x] `applicationId` set to `io.github.nathanrcast.timelapse` and the label to `TimeLapse+` (Java namespace unchanged; see T03).
- [x] LICENSE keeps the upstream copyright, README has credits, and AGENTS.md/CLAUDE.md are in place.
- [x] `.gitignore` covers APKs, keystores and `local.properties`. Committed release APKs and the `.iml` are untracked.
- [x] `docs/plan.md`, `docs/workflow.md`, `docs/hw_test_checklist.md` and `docs/tickets/*`
- [x] Issue and PR templates, labels, milestones and one issue per ticket.
