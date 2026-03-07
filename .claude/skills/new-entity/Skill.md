---
name: ChefAI New Entity Scaffold
description: Full checklist for adding a Clean Architecture entity to ChefAI: Table → DAO → Domain Model → Repository → Service → DTO → Route. Load when creating any new entity.
---

## Overview

Adding an entity in ChefAI requires touching 9 layers in strict dependency order. Never skip layers or shortcut through them — the Clean Architecture boundary rules are enforced. Domain must never import Ktor, Exposed, or any framework.

---

## Checklist (in order)

### 1. Table — `infrastructure/database/tables/`

```kotlin
object FooTable : LongIdTable("foos") {
    val name = varchar("name", 255)
    val creatorId = long("creator_id").references(UserTable.id)
    val updatedAt = long("updated_at")                             // client millis
    val serverUpdatedAt = timestamp("server_updated_at").index()  // server-authoritative
    val deletedAt = timestamp("deleted_at").nullable()            // soft delete
}
```

**Rules:**
- Column names: `snake_case` — no camelCase (that's the junction-table legacy inconsistency; don't extend it)
- Dual-timestamp: `updated_at` (Long, client millis) + `server_updated_at` (Instant, TIMESTAMPTZ, indexed)
- Soft delete: `deleted_at: Instant?` nullable column
- `UserTable` is the only exception — it uses a single `Instant updated_at`, no `server_updated_at`, no `deleted_at`
- Avoid nullable columns unless the field is genuinely optional
- Register in `DatabaseInit.kt` → `SchemaUtils.createMissingTablesAndColumns()`

### 2. DAO — `infrastructure/database/dao/`

```kotlin
class FooDAO(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<FooDAO>(FooTable)
    var name by FooTable.name
    var updatedAt by FooTable.updatedAt
    var serverUpdatedAt by FooTable.serverUpdatedAt
    var deletedAt by FooTable.deletedAt
}
```

One DAO class per entity. DAOs are infrastructure — they never leave the `infrastructure` package.

### 3. Domain Model — `domain/model/`

```kotlin
data class Foo(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val serverUpdatedAt: Instant,
    val deletedAt: Instant? = null
)
```

- Immutable `data class`
- No framework imports (no Ktor, no Exposed, no kotlinx.serialization here)
- No `@Serializable` on domain models — serialization belongs to DTOs

### 4. Repository Interface — `domain/repository/`

```kotlin
interface FooRepository {
    suspend fun findById(id: Long): Foo
    suspend fun findByUser(userId: Long): List<Foo>
    suspend fun save(foo: Foo): Foo
    suspend fun softDelete(id: Long, userId: Long): Unit
}
```

- All methods `suspend`
- Return domain models — never DAOs
- Throw typed exceptions; never return null or `Result`
- Interface lives in domain — implementation lives in infrastructure

### 5. Mapper — `infrastructure/database/mappers/`

```kotlin
fun FooDAO.toDomain(): Foo = Foo(
    id = id.value,
    name = name,
    updatedAt = updatedAt,
    serverUpdatedAt = serverUpdatedAt,
    deletedAt = deletedAt
)
```

- Pure function, no side effects
- No exceptions thrown inside mappers
- Converts DAO ↔ Domain only — DTOs are mapped separately in routes/services

### 6. Repository Implementation — `infrastructure/database/repositoryImpl/`

```kotlin
class PostgresFooRepository : FooRepository {

    override suspend fun findById(id: Long): Foo = withContext(Dispatchers.IO) {
        transaction {
            FooDAO.findById(id)?.toDomain()
                ?: throw FooNotFoundException("Foo $id not found")
        }
    }

    override suspend fun softDelete(id: Long, userId: Long) = withContext(Dispatchers.IO) {
        transaction {
            val now = Clock.System.now()
            FooTable.update({ (FooTable.id eq id) and (FooTable.creatorId eq userId) }) {
                it[deletedAt] = now
                it[serverUpdatedAt] = now
            }
        }
    }
}
```

**Rules:**
- Always `withContext(Dispatchers.IO)` wrapping `transaction { }`
- Soft delete: set `deleted_at = now`, `server_updated_at = now`
- Never expose DAOs outside this class

### 7. Service Method — `domain/service/`

Add to existing service or create `FooService.kt`:

```kotlin
class FooService(private val fooRepository: FooRepository) {

    suspend fun getFoo(id: Long, requestingUserId: Long): Foo {
        val foo = fooRepository.findById(id)
        if (foo.creatorId != requestingUserId) throw FooAccessDeniedException(...)
        return foo
    }
}
```

**Rules:**
- Services are stateless — all state in DB
- Services depend on repository **interfaces**, never on DAOs or table objects
- Services throw typed exceptions; callers handle HTTP mapping
- If adding a new exception, add it to the sealed hierarchy in `domain/exception/`

### 8. DTOs — `application/dto/`

```kotlin
@Serializable
data class CreateFooRequest(
    val name: String
)

@Serializable
data class FooResponse(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val serverUpdatedAt: Long  // return as millis for client
)
```

**Rules:**
- Always `@Serializable`, always `val` (immutable)
- Separate `Request` vs `Response` models
- No business logic
- Don't expose internal IDs unless the client needs them
- Domain → DTO conversion happens in the route handler or a dedicated mapper

### 9. Route Handler — `presentation/routes/`

```kotlin
fun Route.fooRoutes(fooService: FooService) {
    authenticate("auth-jwt") {
        get("/foos/{id}") {
            val userId = call.principal<JWTPrincipal>()
                ?.payload?.getClaim("userId")?.asLong()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")

            try {
                val foo = fooService.getFoo(id, userId)
                call.respond(foo.toResponse())
            } catch (e: FooNotFoundException) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: FooAccessDeniedException) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to e.message))
            }
        }

        post("/foos") {
            val request = call.receive<CreateFooRequest>()
            // validate → service → respond
        }
    }
}
```

**Rules:**
- All private endpoints inside `authenticate("auth-jwt")`
- Validate all inputs before calling service
- Catch each exception type explicitly — no generic catch
- Never expose stack traces or internal messages
- Call service, not repository, from routes
- Register route in `Application.kt` DI wiring

---

## DI Wiring — `application/service/Application.kt`

```kotlin
val fooRepository: FooRepository = PostgresFooRepository()
val fooService = FooService(fooRepository)
routing {
    fooRoutes(fooService)
}
```

---

## Quick Checklist

- [ ] Table defined in `infrastructure/database/tables/` (snake_case, dual-timestamp, soft delete)
- [ ] Table registered in `DatabaseInit.kt`
- [ ] DAO in `infrastructure/database/dao/`
- [ ] Domain model in `domain/model/` (no framework imports)
- [ ] Repository interface in `domain/repository/`
- [ ] Mapper in `infrastructure/database/mappers/`
- [ ] Repository impl in `infrastructure/database/repositoryImpl/` (IO dispatcher, transactions)
- [ ] Service method in `domain/service/` (uses interface, throws typed exceptions)
- [ ] DTOs in `application/dto/` (@Serializable, immutable, Request/Response split)
- [ ] Route handler in `presentation/routes/` (authenticated, validates, catches exceptions)
- [ ] DI wired in `Application.kt`
- [ ] Unit tests with `Fake*Repository` in test package
- [ ] Integration test with `*IntegrationTest.kt`
