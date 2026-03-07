# ChefAI Backend — Claude Project Instructions

> Project: Ktor backend for the Chef AI recipe app
> Package: `com.tenmilelabs`
> Stack: Kotlin + Ktor + Exposed ORM + PostgreSQL + JWT

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Web framework | Ktor 3.2.3 (Netty engine) |
| ORM | Exposed 0.56.0 (DSL + DAO) |
| Database | PostgreSQL 42.7.4 (prod), H2 (test) |
| Auth | JWT HS256 via `ktor-server-auth-jwt`, BCrypt cost 12 |
| Serialization | `kotlinx.serialization` with `@Serializable` |
| Testing | JUnit 5 (Jupiter), `ktor-server-test-host`, JsonPath |
| Build | Gradle 8 with Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |

---

## Clean Architecture Layers

```
domain/         ← Framework-independent. Models, repository interfaces, services, exceptions.
application/    ← DTOs, module setup (Application.kt), DI wiring.
infrastructure/ ← DB tables, DAOs, mappers, repository impls, JWT config, plugins.
presentation/   ← Ktor route handlers. No business logic here.
```

**Strict rules:**
- Domain layer must not import Ktor, Exposed, or any framework.
- Repositories: interface in `domain.repository`, implementation in `infrastructure.database.repositoryImpl`.
- Services: never touch DB directly — call repository interfaces only.
- Routes: never contain business logic — delegate to services only.
- Mappers: pure functions, no side effects, no exceptions thrown inside them.

---

## Package Structure (actual paths)

```
src/main/kotlin/
  application/
    dto/                  # Request/Response DTOs (@Serializable, immutable)
    service/Application.kt  # Module setup, DI, lifecycle hooks
  domain/
    exception/            # Sealed AuthException hierarchy
    model/                # User, Recipe, RefreshToken (immutable data classes)
    repository/           # Repository interfaces
    service/              # AuthService, RecipesService, SyncService, SoftDeletePurgeService
  infrastructure/
    auth/                 # JWT config and utilities
    database/
      dao/                # UserDAO, RecipeDAO, RefreshTokenDAO
      mappers/            # DAO ↔ Domain converters
      repositoryImpl/     # PostgresUserRepository, PostgresRecipesRepository
      tables/             # Exposed table definitions (12 tables)
      DatabaseInit.kt
      Databases.kt
    plugins/              # Serialization, content negotiation
  presentation/
    middleware/
    routes/               # AuthRoutes, Routing (recipes), SyncRoutes

src/test/kotlin/          # Unit tests (*Test.kt) + integration tests (*IntegrationTest.kt)
```

---

## Database Conventions

### Table naming
- Entity tables: `snake_case` column names (`recipe_id`, `image_url`, `server_updated_at`)
- Junction tables (`RecipeIngredientTable`, `RecipeTagTable`, `RecipeLabelTable`): currently use `camelCase` (`recipeId`) — **this is a known inconsistency, do not add new camelCase columns**

### Dual-timestamp pattern (for offline-first sync)
All entity tables (except `users`) have:
- `updated_at: Long` — client-submitted milliseconds, not server-authoritative
- `server_updated_at: Instant (TIMESTAMPTZ)` — set by server on every write, indexed, used for sync pagination

**UserTable exception**: uses `Instant` for both `created_at`/`updated_at`, no `deleted_at`, no `server_updated_at`.

### Soft delete
- Entity tables have `deleted_at: Instant?` (nullable)
- Soft-delete: set `deleted_at = now`, update `server_updated_at`
- Hard purge: `SoftDeletePurgeService` runs on schedule (configurable, default: daily, 90-day retention)

### Foreign keys
- `RecipeTable.creator_id` → `UserTable.id` (FK exists)
- Junction tables do NOT have FKs back to ingredient/tag/label masters (intentional)

### DB operations
- Always run in Exposed `transaction { }` block
- Use IO dispatcher for all DB work
- Avoid nullable columns unless a field is genuinely optional

---

## Exception Handling

Sealed hierarchy in `domain.exception`:
```kotlin
sealed class AuthException(message: String) : Exception(message)
class ValidationException(message: String) : AuthException(message)
class UserAlreadyExistsException(message: String) : AuthException(message)
class InvalidCredentialsException(message: String) : AuthException(message)
class InvalidRefreshTokenException(message: String) : AuthException(message)
class TokenReuseDetectedException(message: String) : AuthException(message)
class UserNotFoundException(message: String) : AuthException(message)
class AuthInternalException(message: String, cause: Throwable? = null) : AuthException(message)
```

**HTTP mapping (routes catch-blocks):**
- `ValidationException` → 400
- `UserAlreadyExistsException` → 409
- `InvalidCredentialsException` / `InvalidRefreshTokenException` / `TokenReuseDetectedException` → 401
- `AuthInternalException` → 500

**Rules:**
- Services throw typed exceptions; never return null or `Result` to signal expected failures.
- Routes always catch each exception type explicitly — no generic `catch(e: Exception)` that swallows errors silently.
- Never expose stack traces or internal messages to API callers.

---

## Authentication & Security

- **JWT**: HS256, 1h expiry, claims: `userId`, `email`, `jti`, `iat`, `exp`
- **Refresh tokens**: 64-byte random → SHA-256 hash stored; never stored plain
- **Token rotation**: new tokens issued on refresh, old token revoked atomically
- **Reuse detection**: revoked token used → revoke ALL user tokens
- **Passwords**: BCrypt cost 12; validated 8-128 chars, requires letter + digit
- **Input validation**: XSS patterns rejected, SQL injection prevented via Exposed prepared statements
- **All private endpoints**: require JWT auth via `authenticate("auth-jwt")`
- **Secrets**: always environment-injected (`JWT_SECRET`, DB credentials) — never hardcoded

---

## Sync Protocol

- **POST /sync/push**: accepts `SyncPushRequest` (client recipe batch), returns `{ accepted, conflicts, errors }`
- **GET /sync/pull?since=<ms>&limit=100**: returns recipes with `server_updated_at > since`, cursor-paginated

Sync state is tracked per junction table row (`syncState` column in `RecipeIngredientTable`, `RecipeTagTable`, `RecipeLabelTable`).

---

## DTO Conventions

- Always `@Serializable`
- Always `val` fields (immutable)
- No business logic inside DTOs
- Separate Request vs Response models (e.g., `CreateRecipeRequest`, `RecipeResponse`)
- Do not expose internal IDs unless the client genuinely needs them

---

## Testing

```
Unit tests    → *Test.kt       — use Fake* repositories (FakeUserRepository, FakeRecipesRepository, FakeRefreshTokenRepository)
Integration   → *IntegrationTest.kt — use ktor-server-test-host with real H2 in-memory DB
```

**Rules:**
- Unit tests: mock all repositories with fake implementations, no real DB
- Tests must be deterministic (no randomness, no time.now() without injection)
- No real external API calls in tests
- Do not use `@Ignore` to skip flaky tests — fix them

Test users (seeded): `test1@ex.com`, `test2@ex.com`, `test3@ex.com` / password: `test123!`

---

## Build & Run

```bash
./gradlew build          # Full build + test
./gradlew run            # Run locally (H2 by default)
./gradlew buildFatJar    # Executable JAR
./gradlew test           # Tests only
./gradlew test --tests "*SyncRoutesIntegrationTest"  # Single class

docker-compose up db                               # PostgreSQL only
docker-compose up --build                          # Full stack
docker-compose -f docker-compose.smoketest.yaml up # E2E smoke tests
```

---

## Code Generation Rules (from firebender.json)

These rules apply whenever generating or modifying code in this project:

### General
- Idiomatic Kotlin — no Java idioms, no `!!`, explicit return types on public functions
- No secrets or credentials in code — all via environment
- Services must be stateless; state in DB only
- Never return nullable types from services/repositories — throw typed exceptions
- Inject dependencies; never instantiate them inside classes

### Adding a new entity (checklist)
1. Table definition in `infrastructure/database/tables/` — snake_case columns, dual-timestamp pattern
2. DAO in `infrastructure/database/dao/`
3. Domain model in `domain/model/`
4. Repository interface in `domain/repository/`
5. Repository implementation in `infrastructure/database/repositoryImpl/`
6. Mapper in `infrastructure/database/mappers/`
7. Service method in `domain/service/` (use repository interface, never DAO directly)
8. DTOs in `application/dto/`
9. Route handler in `presentation/routes/`

### Adding a new route
1. Authenticate all private endpoints with `authenticate("auth-jwt")`
2. Validate all inputs before touching the DB
3. Call service, not repository, from routes
4. Map service exceptions to HTTP status codes
5. Return typed response DTOs, never raw domain models

---

## Known Issues & Gotchas

| Issue | Location | Status |
|-------|----------|--------|
| `publicRecipes()` queries `"public"` but Privacy enum is `PUBLIC` | `RecipesRepository` impl | Bug — needs fix |
| Junction table columns use camelCase (`recipeId`) | `RecipeIngredientTable`, etc. | Inconsistency — don't extend |
| `removeRecipe()` was hard delete | Fixed in PR #22 (soft delete) | Resolved |

---

## Claude Skills

Project skills live in `.claude/skills/`. Load the relevant skill when working in that area — each skill provides the detailed reference and code examples for that domain.

| Skill | Load when... |
|-------|-------------|
| `auth-flow` | Working on JWT, refresh tokens, BCrypt, token rotation, or `AuthService` / `AuthRoutes` |
| `new-entity` | Scaffolding any new Clean Architecture entity from scratch (Table → Route) |
| `sync-debug` | Debugging push/pull sync, timestamp issues, syncState, or soft delete lifecycle |

---

## Related Project

Android client: `/Users/jmucientes/AndroidStudioProjects/ChefAI/`
Sync RFC: `docs/rfcs/rfc-001-offline-first-sync.md` (in Android project)
