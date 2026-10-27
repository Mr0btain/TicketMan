
<!-- What this changes, and why. Link the issue if there is one. -->

## Checklist

- [ ] Tests pass: `./gradlew :server:test :shared:jvmTest :composeApp:desktopTest`
- [ ] New behaviour has tests (see `docs/testing.md`)
- [ ] **Docs updated?**
- [ ] `python3 scripts/check_api_docs.py` passes
- [ ] UI changed: Render tests pass inspection.
- [ ] Database changed: new columns have a default or are nullable, and the table is in `migratedTables`
      (or `addUserColumns()`); tried on a data folder from before the change
- [ ] `scripts/counter_e2e.py` against a fresh server pases
