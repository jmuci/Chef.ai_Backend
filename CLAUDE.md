# ChefAI Backend — Claude Project Context

**Project**: Ktor-based backend for offline-first recipe sync
**Tech Stack**: Kotlin, Ktor, Exposed ORM, Kotlinx.serialization, Kotlin Coroutines
**Architecture**: Clean Architecture with cursor-based sync protocol
**Status**: Production (Sync v1 complete, Auth v1 complete)

---

## Stack & Conventions

### Kotlin & Build
- **Language**: Kotlin only (no Java)
- **Build**: Gradle with `libs.versions.toml`
- **Testing**: JUnit4, Turbine for Flow, Testcontainers for integration tests
- **Serialization**: kotlinx.serialization (not Jackson)
- **Async**: Coroutines + Flow, `suspendTransaction` for DB operations

### Architecture: Clean Architecture Layers

```
presentation (Controllers/Routes)
  ↓ depends on
application (Use Cases, DTOs, Services)
  ↓ depends on
domain (Entities, Interfaces, Business Logic)
  ↓
infrastructure (Exposed ORM, DB, External Services)
```

**Rules**:
- Domain layer: **Zero framework dependencies**. No Ktor, no Exposed, no context receivers — pure Kotlin.
- Services: Stateless. State only in DB or caches.
- DTOs: Immutable, use `@Serializable`, no business logic.
- Repositories: Interface in domain, impl in infrastructure.
- Error handling: Typed sealed classes + explicit throws (not Result/Either in this project).

### Exposed ORM
- Table names: **snake_case**
- Avoid nullable fields unless required
- Use `suspendTransaction` for coroutine-safe DB access
- Minimal explicit transactions; Exposed handles most atomicity
- Queries: Use DSL builders, not raw SQL

**Example**:
```kotlin
suspend fun findRecipes(userId: UUID, since: Long): List<Recipe> = suspendTransaction {
    RecipeTable.selectAll().where {
        (RecipeTable.server_updated_at greater Instant.fromEpochMilliseconds(since))
            .and(RecipeTable.creator_id eq userId.toString())
    }.map { mapToRecipe(it) }
}
```

### Ktor & Routing
- Authenticate all private endpoints with JWT bearer token
- Validate input **before** touching DB
- Use proper HTTP status codes (201 for created, 400 for validation, 401 for auth, 409 for conflict)
- Do **not** expose stack traces or internal error messages
- Return typed error responses: `{ "error": { "code": "...", "message": "..." } }`

**Example**:
```kotlin
post("/sync/push") {
    val auth = call.authenticate()
    val request = call.receive<SyncPushRequest>()

    val response = syncService.pushRecipes(auth.userId, request)
    call.respond(HttpStatusCode.OK, response)
}
```

---

## Sync Protocol (Critical)

**Reference**: See `docs/sync-protocol.md` for full details.

### Key Concepts
- **Cursor-based**: Client tracks `since` (server timestamp). Server sends only `server_updated_at > since`.
- **Delta vs. Gap**: Union pattern ensures FK safety:
  - **Delta**: `server_updated_at > since` (changes client needs)
  - **Gap**: Referenced by current page but older (prevents FK failures)
- **Reference Data**: Every pull/push conflict includes all FK dependencies (ingredients, tags, labels, allergens, sourceClassifications).

### Sync Endpoints

**GET /sync/pull?since={ms}&limit={n}**
- Returns recipes + all reference data (union of delta + gap)
- Response includes `serverTimestamp` (client's new cursor) and `hasMore` (pagination)
- Supports pagination via cursor chaining

**POST /sync/push**
- Per-recipe processing (accept/conflict/error independent)
- Conflict check: `existing.serverUpdatedAtMillis > recipe.updatedAt`
- Conflict response includes `referenceData` for server version (enables one-step resolution)
- Errors are per-recipe; batch doesn't fail

### Union Pattern (Reference Data)
```kotlin
// In collectReferenceData:
// sinceMillis non-null → pull mode (delta + gap)
// sinceMillis null → push conflict mode (gap only)
WHERE server_updated_at > :since OR uuid IN (:referencedIds)
```

**Why**: Prevents Room FK constraint failures on Android client when upserting recipes.

---

## Testing Requirements

### Rules (Non-Negotiable)
1. **Always update tests when changing code**
   - Modify existing test cases if logic changes
   - Add new test cases if new behavior is introduced
   - If a test fails after your change, fix it or add a new one — don't delete passing tests

2. **Test Coverage by Type**
   - **Unit tests** (`*Test.kt`): Mock repositories, test business logic
   - **Integration tests** (`*IntegrationTest.kt`): Use fake in-memory repos, test full routes via HTTP
   - **Both** must pass before considering work complete

3. **Test Organization**
   - `src/test/kotlin/domain/service/` — Service unit tests
   - `src/test/kotlin/infrastructure/auth/` — Route integration tests
   - Fakes: `FakeSyncRepository`, `FakeUserRepository`, etc. in same package or `infrastructure/database/`

4. **Sync-Specific Testing**
   - Test delta vs. gap logic (e.g., old tag referenced by new recipe)
   - Test conflict scenarios with assertions on `serverVersion` + `referenceData`
   - Test pagination with cursor chaining
   - Test multi-recipe batch processing (accept + conflict + error in single push)

**Example Test**:
```kotlin
@Test
fun pullIncludesGapTagReferencedByReturnedRecipe() = testApplication {
    val syncRepository = FakeSyncRepository()
    val tagId = syncRepository.seedTag(serverUpdatedAt = 500L) // old tag
    val recipe = seedRecipe(..., tagIds = listOf(tagId.toString())) // new recipe refs old tag

    val response = client.get("/sync/pull?since=1000&limit=10")
    val body = response.body<SyncPullResponse>()

    assertEquals(1, body.recipes.size)
    assertTrue(body.tags.any { it.uuid == tagId.toString() }) // gap clause fires
}
```

---

## Documentation Maintenance

### Rule: Update Docs at Session End
**Before exiting, check and update relevant documentation**:
- If adding new endpoints → update `docs/sync-protocol.md` or create endpoint doc
- If changing sync behavior → update protocol doc with examples
- If adding auth flow → update `docs/auth-architecture.md`
- If errors are new → update `docs/exception-handling.md`
- If README is outdated → refresh it

**Prompt user**: At end of session, ask: *"Should I update any documentation given the changes made?"*

### Documentation Files
- `docs/sync-protocol.md` — Cursor-based sync, conflict resolution, multi-device examples
- `docs/auth-architecture.md` — JWT, refresh tokens, auth flow
- `docs/auth-quick-start.md` — Quick endpoint reference
- `docs/exception-handling.md` — Error codes and patterns
- `README.md` — Quick start, smoke tests, build commands

---

## Code Style & Practices

### Kotlin Idioms (Strict)
- **No `!!` operator** — Use `requireNotNull(x) { "reason" }`, `?: return`, or `?.let { }`
- **No nullable returns** — Throw typed exceptions; caller handles them
- **Explicit return types** on all public functions
- **Data classes** for pure data; never for objects with identity semantics
- **Sealed interfaces** preferred over sealed classes for State/Event/Error types

### Immutability
- Use `data class` with `copy()` for modifications
- Prefer `List`, `Map`, `Set` over mutable variants
- Avoid `var` at module scope; use `val` + reassignment or function returns

### Error Handling
```kotlin
// Good: Typed sealed class
sealed class SyncError {
    data class CreatorMismatch(val recipeId: UUID) : SyncError()
    data class IngredientNotFound(val ingredientId: UUID) : SyncError()
}

// Bad: Generic Exception
throw Exception("Something went wrong")
```

### Naming
- Functions: `camelCase`, verb-first for actions (`fetchRecipes`, not `recipesFetch`)
- Classes: `PascalCase`
- Constants: `SCREAMING_SNAKE_CASE`
- Database columns: `snake_case`
- DTOs: Reflect API contract (`SyncRecipe`, not `RecipeDto`)

---

## Common Patterns

### Service Layer
```kotlin
class SyncService(
    private val repo: SyncRepository,
    private val log: Logger
) {
    suspend fun pushRecipes(userId: UUID, request: SyncPushRequest): SyncPushResponse {
        // Validate, process, return response
        // Never throw unhandled exceptions; map to error response
    }
}
```

### Repository Interface (Domain)
```kotlin
interface SyncRepository {
    suspend fun getRecipe(uuid: UUID): SyncRecipeRecord?
    suspend fun upsertRecipeAggregate(recipe: SyncRecipe, serverUpdatedAt: Instant)
    suspend fun collectReferenceData(
        sinceMillis: Long?,
        ingredientIds: Set<UUID>,
        tagIds: Set<UUID>,
        labelIds: Set<UUID>
    ): SyncReferenceData
}
```

### Ktor Route (Presentation)
```kotlin
fun Route.syncRoutes(syncService: SyncService) {
    authenticate {
        post("/sync/push") {
            val userId = call.getUserId() // from JWT
            val request = call.receive<SyncPushRequest>()
            val response = syncService.pushRecipes(userId, request)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
```

### Exposed Query with Union
```kotlin
private fun queryTags(sinceInstant: Instant?, ids: Set<UUID>): List<SyncTag> {
    if (sinceInstant == null && ids.isEmpty()) return emptyList()
    return TagTable.selectAll().where {
        when {
            sinceInstant != null && ids.isNotEmpty() ->
                (TagTable.server_updated_at greater sinceInstant) or (TagTable.id inList ids)
            sinceInstant != null ->
                TagTable.server_updated_at greater sinceInstant
            else ->
                TagTable.id inList ids
        }
    }.map { mapToSyncTag(it) }
}
```

---

## Firebender Agent Rules (Enforced)

From `agents/firebender.json`:

### Domain Layer
- Must not depend on any framework
- Repositories must be interfaces (domain) + impl (infrastructure)
- Use typed exceptions; caller handles them
- Inject dependencies; never instantiate
- Services must be stateless

### Database (Exposed)
- Tables: snake_case
- Avoid nullable unless required
- Use `suspendTransaction` for coroutine safety
- Run DB ops on IO dispatcher
- Hash + pepper passwords

### API (Ktor)
- Authenticate all private endpoints
- Validate input before DB
- No business logic in controllers
- Proper HTTP status codes
- No stack traces or internal errors

### Testing
- Unit tests mock repositories
- Integration tests use fakes (in-memory)
- Tests must be deterministic
- No external API calls

### DTOs
- No business logic
- Immutable
- `@Serializable`
- Reflect API contract
- Don't expose internal IDs unless needed

### Global Rules
- Idiomatic Kotlin
- No insecure cryptography
- Secrets via environment (not hardcoded)
- Services remain stateless
- Clean Architecture boundaries
- Prefer immutability + pure functions

---

## Skills & Tools

### Available Skills
- **commit**: Git commit workflow (reads changes, drafts message, commits)
- **review-pr**: GitHub PR review

### Recommended Tools
- **Explore**: Fast codebase exploration (find patterns, search keywords)
- **Plan**: Design implementation before writing (for non-trivial changes)
- **Task**: Parallel agent execution (tests, builds, linting)

### How to Use
1. **Before major changes**: Use Plan to design approach
2. **After code changes**: Always run tests via Task
3. **Before commit**: Use commit skill to draft message

---

## Workflow Checklist

When adding a feature or fixing a bug:

- [ ] **Code**: Write implementation (follow Clean Architecture)
- [ ] **Tests**: Add or update unit + integration tests
- [ ] **Lint**: Ensure Kotlin idioms (no `!!`, explicit types, etc.)
- [ ] **Run**: `./gradlew test` — all tests pass
- [ ] **Docs**: Update relevant docs (sync-protocol, auth, exceptions, README)
- [ ] **Commit**: Use skill to draft + commit message
- [ ] **Prompt**: Ask user if docs need updates before session ends

---

## Quick Commands

| Task | Command |
|------|---------|
| Run all tests | `./gradlew test` |
| Run sync tests | `./gradlew test --tests "*Sync*"` |
| Run server | `./gradlew run` or `docker compose up --build` |
| Build fat JAR | `./gradlew buildFatJar` |
| Format code | `./gradlew ktlintFormat` (if configured) |
| DB shell | `psql -h localhost -U postgres -d chefai_db` |

---

## Key Files & Structure

```
src/main/kotlin/
├── domain/
│   ├── repository/          ← Interfaces (SyncRepository, etc.)
│   ├── service/             ← Business logic (SyncService, etc.)
│   └── model/               ← Domain entities (no framework deps)
├── application/
│   ├── dto/                 ← Request/response DTOs (@Serializable)
│   └── service/             ← Application services (orchestration)
├── infrastructure/
│   ├── database/
│   │   ├── tables/          ← Exposed table definitions
│   │   └── repositoryImpl/   ← Repository implementations
│   ├── auth/                ← JWT, auth routes
│   └── http/                ← HTTP clients (if any)
└── presentation/
    └── routes/              ← Ktor routes (controllers)

src/test/kotlin/
├── domain/service/          ← Service unit tests
├── infrastructure/auth/     ← Integration tests (routes)
└── infrastructure/database/ ← Fake repositories

docs/
├── sync-protocol.md         ← Cursor-based sync, conflicts, examples
├── auth-architecture.md     ← JWT, refresh, auth flow
├── auth-quick-start.md      ← Endpoint reference
├── exception-handling.md    ← Error codes
└── README.md                ← Build, run, smoke tests
```

---

## Red Flags & Gotchas

🚩 **Sync-specific**:
- Missing reference data in pull/conflict response → FK failures on client
- Delta-only without gap clause → old tags/labels lost on client
- Transitive FKs not fetched (ingredient's allergen) → client crashes
- Server timestamp used as cursor without monotonic guarantee → out-of-order deltas

🚩 **Architecture**:
- Domain importing from Ktor/Exposed → layering violation
- Mutable state in services → concurrency bugs
- Hardcoded secrets in code → security breach
- Throwing generic `Exception` → caller can't distinguish errors

🚩 **Testing**:
- Tests only checking happy path → miss real failures
- Real DB in unit tests → slow, flaky, not isolated
- No pagination tests → cursor bug slips through
- Test changes without modifying tests → broken contract

---

## Session End Checklist

Before finishing:

1. ✅ **Tests pass**: `./gradlew test` shows all green
2. ✅ **Code follows style**: No `!!`, explicit types, Clean Architecture
3. ✅ **Docs updated**: Sync, auth, errors, README if applicable
4. ✅ **Prompt user**: *"Should I update any documentation before we wrap?"*

---

## Contact & References

- **Ktor Docs**: https://ktor.io/docs/home.html
- **Exposed Docs**: https://github.com/JetBrains/Exposed
- **Kotlin Coroutines**: https://kotlinlang.org/docs/coroutines-overview.html
- **Clean Architecture**: *Clean Architecture* by Robert C. Martin

---

**Generated**: 2026-03-04
**Architecture**: Clean Architecture + Cursor-Based Sync
**Last Updated**: Post sync-protocol.md + lifecycle tests
