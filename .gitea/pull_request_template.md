## What and why

<!-- What this changes, and why. Link the issue if there is one. -->

## Checklist

- [ ] Tests pass: `./gradlew :server:test :shared:jvmTest :composeApp:desktopTest`
- [ ] New behaviour has tests (see `docs/testing.md`)
- [ ] **Docs updated?** Any page in `docs/` this makes wrong is fixed, and new pages are in `docs/README.md` and
      `docs/PLAN.md`. `CLAUDE.md` too if it covers the area
- [ ] Routes added, moved or re-guarded: `python3 scripts/check_api_docs.py` passes
- [ ] UI changed: looked at the render tests' PNGs (`composeApp/build/screen-renders/`, `build/kit-gallery/`), dark and light
- [ ] Database changed: new columns have a default or are nullable, and the table is in `migratedTables`
      (or `addUserColumns()`); tried on a data folder from before the change
- [ ] Check-in, pickup, invoices, stations or PIN login changed: ran `scripts/counter_e2e.py` against a fresh server
- [ ] A choice someone might want to undo later: added to `docs/decisions.md`
