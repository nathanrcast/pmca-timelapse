# T03: Merge the holy-grail fork

**Labels:** type:feature area:camera size:M hw:required · **Milestone:** Wave 1 · **Depends on:** T01

## Goal
Bring in the holy-grail auto exposure ramping from the Frosbe fork (shared history, 11 commits ahead of the upstream base), and settle the final Java namespace.

## Context
- Remote `frosbe` → `Frosbe/SonyHolyGrailTimelapse`, branch `master`.
- The fork moves sources to `com/frosbe/holygrail/`, splits `Settings.java`, adds `Util.java`, rewrites `activity_settings.xml`/`activity_shoot.xml`, adds the dependency `com.jjoe64:graphview:3.1.4`, and commits a `local.properties` (drop that).
- When holy grail is off it is meant to behave like upstream.

## Tasks
1. `git merge frosbe/master`, resolving conflicts in favor of the T01 build files. Keep our `applicationId`, label, LICENSE and `.gitignore`.
2. Move everything to the namespace `io.github.nathanrcast.timelapse` in a **separate commit** (sources, manifest, `namespace`, layouts' custom view tags).
3. Check that graphview 3.1.4 resolves from Maven Central and is API-10-safe. If it isn't, vendor it or replace it with a tiny Canvas view.
4. Add a copyright line for Frosbe to LICENSE.txt (the fork never changed the license).
5. Keep the holy-grail settings behind the existing toggle, defaulting to **off**.

## Acceptance
- [ ] Build and lint pass. `git log --follow` still shows upstream history for the moved files.
- [ ] Holy grail off gives the same settings screen options and shot behavior as before the merge (agent checks code paths; maintainer checks on camera).
- [ ] **HW:** a 30-shot holy-grail run on the a6500 at dusk, or with a changing light source, shows the shutter speed/ISO ramping in EXIF; NEX-5T launches and runs with holy grail off (pending maintainer).
