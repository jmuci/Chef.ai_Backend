# ChefAI Backend — Quick Reference Card

## 🎯 Project at a Glance

| Aspect | Detail |
|--------|--------|
| **Tech Stack** | Kotlin, Ktor, Exposed ORM, Kotlinx.serialization |
| **Architecture** | Clean Architecture (domain → application → infrastructure) |
| **Sync Protocol** | Cursor-based, conflict-resolving, FK-safe (see `docs/sync-protocol.md`) |
| **Auth** | JWT + refresh tokens (see `docs/auth-architecture.md`) |
| **Tests** | JUnit4 + Testcontainers integration tests |
| **Build** | Gradle with `libs.versions.toml` |

## 🔑 Core Rules

### Code
- ❌ No `!!` operator — use `requireNotNull()`, `?: return`, `?.let {}`
- ❌ No nullable returns — throw typed exceptions
- ❌ No hardcoded secrets — use environment variables
- ✅ Services stateless (state in DB/caches only)
- ✅ Immutable data classes with `copy()` for changes
- ✅ Clean Architecture: domain has ZERO framework deps

### Testing
- Always update tests after code changes
- Add new test cases if new behavior introduced
- Unit tests (`*Test.kt`) mock repositories
- Integration tests (`*IntegrationTest.kt`) use fakes
- Sync tests must cover delta + gap scenarios

### Documentation
- Update `docs/` if behavior changed
- Link new docs in README's Documentation index
- **Before session ends, prompt user to review docs**

## 📦 File Structure

```
src/main/kotlin/
├── domain/           ← No framework (pure Kotlin)
│   ├── repository/   ← Interfaces
│   └── service/      ← Business logic
├── application/      ← Use cases, DTOs, services
│   └── dto/          ← @Serializable DTOs
├── infrastructure/   ← DB, HTTP, auth
│   └── database/     ← Exposed tables + impls
└── presentation/     ← Ktor routes

src/test/kotlin/
├── domain/service/   ← Unit tests
└── infrastructure/   ← Integration tests
```

## 🔄 Sync Protocol TL;DR

**GET /sync/pull?since={ms}&limit={n}**
- Returns recipes + all reference data
- Data includes **delta** (changed since cursor) + **gap** (old but referenced)
- Prevents FK failures on Android client
- Response: recipes, ingredients, tags, labels, allergens, sourceClassifications

**POST /sync/push**
- Per-recipe accept/conflict/error (independent)
- Conflict check: `server_updated_at > client_updated_at`
- Conflict response includes `referenceData` (no extra pull needed)

**Key Pattern**: `WHERE server_updated_at > :since OR uuid IN (:referencedIds)`

## ⚡ Quick Commands

```bash
# Test
./gradlew test                          # All tests
./gradlew test --tests "*Sync*"         # Only sync tests

# Dev
./gradlew run                           # Start server
docker compose up --build               # With Postgres

# Build
./gradlew buildFatJar                   # Fat JAR for deployment

# DB
psql -h localhost -U postgres -d chefai_db
```

## 📚 Documentation

| File | When to Update |
|------|---------|
| `docs/sync-protocol.md` | Sync endpoints/behavior change |
| `docs/auth-architecture.md` | Auth flow change |
| `docs/exception-handling.md` | New error types |
| `README.md` | Build/run instructions change |

## 🚩 Red Flags

| Problem | Symptom | Fix |
|---------|---------|-----|
| Missing reference data | Android FK fails on upsert | Add to `collectReferenceData` |
| Delta-only fetch | Old tags disappear on client | Add gap clause: `OR uuid IN (...)` |
| Transitive FKs missing | Allergen FK fails | Derive from returned ingredients |
| Test not updated | Code change, test still passes (lies) | Update or add new test case |

## 🔗 Key Files

| File | Purpose |
|------|---------|
| `CLAUDE.md` | This project's conventions + rules |
| `agents/firebender.json` | Architecture enforcement rules |
| `.claude/launch.json` | Dev server configurations |
| `.claude/dev-checklist.md` | Pre-commit quality checks |
| `docs/sync-protocol.md` | Cursor-based sync in detail |

## 📋 Before Commit

- [ ] Tests pass: `./gradlew test`
- [ ] No `!!`, all types explicit
- [ ] Clean Architecture boundaries
- [ ] Docs updated (or prompt user)
- [ ] Commit message clear

## 💡 Common Patterns

**Service (stateless, throws typed errors)**:
```kotlin
suspend fun pushRecipes(userId: UUID, request: SyncPushRequest): SyncPushResponse
```

**Repository Interface (domain)**:
```kotlin
interface SyncRepository {
    suspend fun collectReferenceData(sinceMillis: Long?, ...): SyncReferenceData
}
```

**Ktor Route (authenticated)**:
```kotlin
post("/sync/push") {
    val userId = call.authenticate().userId
    val response = syncService.pushRecipes(userId, call.receive())
    call.respond(HttpStatusCode.OK, response)
}
```

**Union Query (Exposed)**:
```kotlin
where {
    (table.server_updated_at greater sinceInstant) or (table.id inList ids)
}
```

## 🎓 Learn More

- `docs/sync-protocol.md` — Full protocol with 4-step conflict example
- `docs/auth-architecture.md` — JWT + refresh token flow
- CLAUDE.md (this project) — All conventions in detail
- `agents/firebender.json` — Architecture rules
- `README.md` — Build, run, smoke tests

---

**Last Updated**: 2026-03-04 | **Status**: Production-ready
