# Development Checklist

Use this checklist to ensure code quality before committing.

## Code Changes
- [ ] Code follows Clean Architecture (domain → application → infrastructure → presentation)
- [ ] No `!!` operators; use `requireNotNull()` or `?.let {}`
- [ ] All public functions have explicit return types
- [ ] Data classes used for pure data (not objects with identity)
- [ ] No hardcoded secrets (use environment variables)
- [ ] Services are stateless (state in DB or caches only)

## Testing
- [ ] All unit tests pass: `./gradlew test`
- [ ] Added/updated relevant unit tests (`*Test.kt`)
- [ ] Added/updated relevant integration tests (`*IntegrationTest.kt`)
- [ ] New sync behavior tested with delta + gap scenarios
- [ ] Conflict scenarios tested if applicable
- [ ] Pagination tested if changes affect cursors

## Sync-Specific (if touched)
- [ ] Reference data includes all FK dependencies
- [ ] Union pattern used (delta + gap) in `collectReferenceData`
- [ ] Transitive FKs handled (allergens from ingredients)
- [ ] `sinceMillis: Long?` correctly handles pull vs. push modes
- [ ] Conflict response includes `referenceData`

## Documentation
- [ ] Updated relevant docs if behavior changed
  - `docs/sync-protocol.md` — if sync endpoints or behavior changed
  - `docs/auth-architecture.md` — if auth flow changed
  - `docs/exception-handling.md` — if new error types added
  - `README.md` — if build/run instructions changed
- [ ] Added code examples if new pattern introduced
- [ ] Updated README's Documentation index if new docs created

## Commit Preparation
- [ ] Git status clean (no untracked files except `.idea/`, `build/`, etc.)
- [ ] Commit message clear and concise
- [ ] Firebender rules followed (see `agents/firebender.json`)
- [ ] No test files modified without corresponding implementation changes

## Pre-Push
- [ ] All tests pass locally
- [ ] No console warnings or deprecation notices
- [ ] Code formatted (idiomatic Kotlin)
- [ ] No debug `println()` or `TODO()` left behind

---

## Quick Commands

```bash
# Run all tests
./gradlew test

# Run only sync tests
./gradlew test --tests "*Sync*"

# Run only route/integration tests
./gradlew test --tests "*IntegrationTest"

# Build fat JAR
./gradlew buildFatJar

# Start dev server
./gradlew run

# Start with Docker + Postgres
docker compose up --build

# Format Kotlin (if ktlintFormat available)
./gradlew ktlintFormat

# Connect to DB
psql -h localhost -U postgres -d chefai_db
```

---

## Common Issues

**Test fails after code change?**
- Don't delete the test; update it to match new behavior
- If new behavior requires new test cases, add them
- If test is no longer relevant, mark as deprecated + add new one

**Sync response missing reference data?**
- Check `collectReferenceData` is called with correct `sinceMillis` and IDs
- Ensure transitive FKs (allergens, source-classifications) are fetched
- Verify union pattern is used, not delta-only

**FK constraint failure on client?**
- Every referenced entity must be in response
- Use union pattern: delta (`server_updated_at > since`) + gap (referenced by page)
- Check that allergens/source-classifications derived from returned ingredients

**Conflict resolution fails?**
- Ensure `referenceData` in push response includes all FK deps
- Test with `updatedAt > serverUpdatedAtMillis` to avoid re-triggering conflict

**Documentation stale?**
- Update docs at end of session, before committing
- Use examples from tests when adding new patterns
- Link new docs in README's Documentation index

---

## Session Template

```markdown
# Session Start

## What we're working on
[Describe feature/bug fix]

## Changes made
- [ ] Code changes in [files]
- [ ] Test updates/additions
- [ ] Documentation updates

## Testing
- [ ] `./gradlew test` passes
- [ ] Sync tests include delta + gap scenarios (if applicable)
- [ ] Integration tests cover HTTP layer (if applicable)

## Documentation
- [ ] Updated [docs/file.md] with changes
- [ ] README index updated (if new docs)

## Next
[What to do next if session continues]
```
