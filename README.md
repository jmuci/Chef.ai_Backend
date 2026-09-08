# chefai

[![Unit Tests Status](https://github.com/jmuci/Chef.ai_Backend/actions/workflows/unit-tests-workflow.yml/badge.svg)](https://github.com/jmuci/Chef.ai_Backend/actions/workflows/unit-tests-workflow.yml)

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need
  to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Documentation

Core architecture and protocol documentation:

| Document | Coverage |
|----------|----------|
| [Sync Protocol](docs/sync-protocol.md) | Cursor-based sync with conflict resolution, reference data integrity, multi-device scenarios, bookmarks, meal plans |
| [Auth Architecture](docs/auth-architecture.md) | Authentication flow, JWT tokens, refresh mechanism |
| [Auth Quick Start](docs/auth-quick-start.md) | Quick reference for auth endpoints |
| [Exception Handling](docs/exception-handling.md) | Error codes and exception patterns |
| [Home Layout SDUI](docs/home-layout-sdui.md) | Server-driven home layout endpoint, component schema, sidecar data, caching and ETag behavior |
| [Recipe Image Architecture](docs/recipe-image-architecture.md) | Recipe hero image blob upload/serving/reclamation — class diagram, upload/serve sequence diagrams, error states, app startup changes |
| [Recipe Search](docs/recipe-search.md) | Postgres full-text recipe search, anonymous access, browse-card taxonomy, ranking, rate limiting |
| [Meal Plan Roadmap](docs/meal-plan-roadmap.md) | Meal-plan generation phases, recipe sourcing strategy |
| [TheMealDB Importer](docs/themealdb-importer.md) | One-off catalog import tool — fetch, map, dedup, idempotent seed SQL |

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                                      |
|------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                                |
| [Static Content](https://start.ktor.io/p/static-content)               | Serves static files from defined locations                                                       |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers               |
| [Exposed](https://www.jetbrains.com/exposed/)                          | Kotlin SQL library and tools: DSL, DAO Framework, ORM`                                           |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                                   |
| [Thymeleaf](https://ktor.io/docs/server-thymeleaf.html)                | Thymeleaf is a modern server-side Java template engine for both web and standalone environments. 

## Verifications 

Unit tests run on PRs in Github

### End to End Smoke Test

```bash
./smoke-test.sh
```

### Sync Endpoints Smoke Test

Use this quick flow to validate sync behavior manually in a running environment.

1. Register and capture auth token + userId
```bash
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email":"sync-smoke@example.com",
    "username":"sync-smoke",
    "password":"TestPassword123!"
  }'
```

2. Push one aggregate recipe
```bash
curl -s -X POST http://localhost:8080/sync/push \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "recipes":[
      {
        "uuid":"11111111-1111-1111-1111-111111111111",
        "title":"Sync Smoke Recipe",
        "description":"Recipe created from smoke test",
        "imageUrl":"https://example.com/image.jpg",
        "imageUrlThumbnail":"https://example.com/thumb.jpg",
        "prepTimeMinutes":10,
        "cookTimeMinutes":20,
        "servings":2,
        "creatorId":"<USER_ID>",
        "recipeExternalUrl":null,
        "privacy":"PRIVATE",
        "updatedAt":1735689600000,
        "deletedAt":null,
        "steps":[
          {
            "uuid":"22222222-2222-2222-2222-222222222222",
            "orderIndex":0,
            "instruction":"Boil water"
          }
        ],
        "ingredients":[
          {
            "ingredientId":"<KNOWN_INGREDIENT_UUID>",
            "quantity":200.0,
            "unit":"g"
          }
        ],
        "tagIds":[],
        "labelIds":[]
      }
    ]
  }'
```

3. Push a bookmark for the recipe
```bash
curl -s -X POST http://localhost:8080/sync/push \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "recipes":[],
    "bookmarkedRecipes":[
      {
        "userId":"<USER_ID>",
        "recipeId":"11111111-1111-1111-1111-111111111111",
        "updatedAt":1735689600001,
        "deletedAt":null
      }
    ]
  }'
```

4. Pull deltas (recipes + bookmarks)
```bash
curl -s "http://localhost:8080/sync/pull?since=0&limit=100" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Expected outcomes:
- Recipe push returns `200` with arrays for `accepted`, `conflicts`, `errors`.
- Bookmark push returns `200` with `bookmarkedRecipes[].syncState == "SYNCED"` and a `serverUpdatedAt` timestamp.
- Pull returns `200` with `recipes`, `bookmarkedRecipes`, `serverTimestamp`, `hasMore`.
- Re-run pull with `since=<serverTimestamp>` to verify cursor-based pagination.
- To remove a bookmark, re-push with `"deletedAt": <timestamp>` — it appears as a tombstone in the next pull.

### Sync Rollout Checklist

- Ensure DB index exists on `recipes.server_updated_at`.
- Ensure DB index exists on `bookmarked_recipes.server_updated_at`.
- Confirm `DELETE /recipes` now performs soft delete (`deleted_at` + `server_updated_at` update).
- Run test suites:
```bash
./gradlew test --tests "*SyncRoutesIntegrationTest"
./gradlew test --tests "*SyncServiceTest"
./gradlew test
```
- Validate push mixed response behavior (accepted/conflicts/errors in one response).
- Validate push bookmark response behavior (`bookmarkedRecipes[].syncState`, `bookmarkErrors`).
- Validate pull pagination behavior (`limit`, `hasMore`, `serverTimestamp` cursor).
- Validate pull includes bookmark deltas and tombstones alongside recipe deltas.

### Recipe Image Smoke Test

See [Recipe Image Architecture](docs/recipe-image-architecture.md) for the full flow, error
states, and diagrams. Quick manual check against a running server:

1. Push a recipe (reuse step 2 of the [Sync Endpoints Smoke Test](#sync-endpoints-smoke-test) above), then upload an image for it:
```bash
IMAGE_BYTES=$(printf '\xff\xd8\xff\xe0\x00\x10JFIF')
HASH=$(printf '%s' "$IMAGE_BYTES" | shasum -a 256 | cut -d' ' -f1)
curl -s -X PUT "http://localhost:8080/recipes/11111111-1111-1111-1111-111111111111/image" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: image/jpeg" \
  -H "X-Content-SHA256: $HASH" \
  --data-binary "$IMAGE_BYTES"
```

2. Fetch it back:
```bash
curl -s -i "http://localhost:8080/recipes/11111111-1111-1111-1111-111111111111/image" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

3. Clear it:
```bash
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
  "http://localhost:8080/recipes/11111111-1111-1111-1111-111111111111/image" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

Expected outcomes:
- Upload returns `200` with `{"imageBlobId", "updatedAt"}`; re-running the same upload is a no-op (`updatedAt` doesn't change).
- Fetch returns `200` with the exact bytes, an `ETag` header, and `Cache-Control: private, max-age=31536000, immutable`.
- A subsequent pull (`GET /sync/pull?...`) includes `imageBlobId` on the recipe.
- Clear returns `204`; a fetch after that returns `404`.

### Recipe Search Smoke Test

`GET /api/v1/recipes/search` is anonymous-capable: with no `Authorization` header it searches the
`PUBLIC` catalog, and with a valid token it searches that catalog *plus* the caller's own
`PRIVATE` recipes. Against a running, seeded server:

1. Anonymous — no auth header at all:
```bash
curl -s "http://localhost:8080/api/v1/recipes/search?q=cake&limit=50" | jq '.results | length'
```

2. Authenticated — same query, with a token from `/auth/login`:
```bash
curl -s "http://localhost:8080/api/v1/recipes/search?q=cake&limit=50" \
  -H "Authorization: Bearer <ACCESS_TOKEN>" | jq '.results | length'
```

3. Broken token — must still be rejected, not silently downgraded to the anonymous scope:
```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  "http://localhost:8080/api/v1/recipes/search?q=cake" \
  -H "Authorization: Bearer not-a-real-jwt"
```

Expected outcomes:
- Step 1 returns `200` with the full public catalog match count — not a subset, and never a `PRIVATE` recipe.
- Step 2 returns a superset of step 1 (the caller's own private recipes are added).
- Step 3 returns `401`; the client is expected to refresh and retry rather than accept public-only results.
- Rate limiting is 30 requests / 10s, keyed per user for authenticated callers and per remote address for anonymous ones.

### Recipe Detail Smoke Test

`GET /api/v1/recipes/{recipeId}` is anonymous-capable the same way search is: a missing
`Authorization` header scopes visibility to `PUBLIC` recipes, and a valid token additionally
allows the caller's own `PRIVATE` recipes. Unlike search, not-found and not-accessible both
respond `404` — see `docs/sync-protocol.md`'s Recipe Detail section. Against a running,
seeded server:

1. Anonymous fetch of a `PUBLIC` recipe:
```bash
curl -s "http://localhost:8080/api/v1/recipes/<PUBLIC_RECIPE_UUID>" | jq '.recipe.uuid, (.referenceData.ingredients | length), (.creators | length)'
```

2. Anonymous fetch of a `PRIVATE` recipe you don't own:
```bash
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/recipes/<PRIVATE_RECIPE_UUID>"
```

3. Owner fetch of their own `PRIVATE` recipe, with a token from `/auth/login`:
```bash
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/recipes/<PRIVATE_RECIPE_UUID>" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

4. Broken token — must still be rejected, not silently downgraded:
```bash
curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080/api/v1/recipes/<PUBLIC_RECIPE_UUID>" \
  -H "Authorization: Bearer not-a-real-jwt"
```

Expected outcomes:
- Step 1 returns `200` with `referenceData`/`creators` populated for that recipe's actual
  ingredients/tags/labels/author — not empty arrays.
- Step 2 returns `404` (not `403` — a private recipe's existence isn't leaked).
- Step 3 returns `200`.
- Step 4 returns `401`.
- Rate limiting is its own bucket from recipe search (30 requests / 10s, same anonymous
  keying) — exhausting one doesn't block the other.

## Test Users

| Email          | Password  |
|----------------|-----------|
| test1@ex.com   | test123!  |
| test2@ex.com   | test123!  |
| test3@ex.com   | test123!  |

## Building & Running

### Prerequisite: `JWT_SECRET`

**The server will not start without it.** It signs access tokens, there is no safe default, and
the app refuses to boot on a missing, too-short (<32 char), or previously-published value rather
than falling back to a guessable one — see
[Auth Architecture § Access Tokens](docs/auth-architecture.md).

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

Keep the same value across restarts: changing it invalidates every access token already issued.
(Refresh tokens are opaque database rows and survive, so clients recover on their next
`/auth/refresh`.) To avoid re-exporting per shell, put it in a `.env` file next to
`docker-compose.yaml` — Docker Compose reads that automatically. **Add `.env` to `.gitignore`
first; it is not currently ignored.**

Without it you get:

```
Exception in thread "main" java.lang.IllegalStateException: Refusing to start: no jwt.secret is
configured. Set the JWT_SECRET environment variable to a random value of at least 32 characters
(e.g. `openssl rand -base64 48`). See docs/auth-architecture.md.
```

### Option A — everything in Docker

The whole stack, app included. Nothing else needs to be running.

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
docker compose -f docker-compose.yaml up --build
```

Server on [http://localhost:8080](http://localhost:8080); Postgres published on 5432. Drop
`--build` to reuse the existing image, and add `-d` to detach. Note `docker compose build` only
builds the image — it does **not** start anything; you still need `up`.

### Option B — Postgres in Docker, app from Gradle or the IDE

The usual loop when you're changing server code and want a debugger attached.

```bash
docker compose up db -d                            # Postgres only
export JWT_SECRET="$(openssl rand -base64 48)"
./gradlew run
```

Stop the database with `docker compose down db`.

Running from the IDE instead of Gradle? `JWT_SECRET` has to be in **the IDE's** run configuration
environment — an `export` in your terminal won't reach it, and the run will fail with the error
above.

To run alongside a container already holding 8080, pass a different port:

```bash
./gradlew run --args="-port=8082"
```

### Gradle tasks

| Task                            | Description                                                          |
|---------------------------------|----------------------------------------------------------------------|
| `./gradlew run`                 | Run the server (needs `JWT_SECRET` + a reachable DB)                 |
| `./gradlew test`                | Run the unit and route tests                                         |
| `./gradlew dbIntegrationTest`   | Run the database-backed tests (see below)                            |
| `./gradlew build`               | Build everything                                                     |
| `./gradlew buildFatJar`         | Build an executable JAR with all dependencies included               |
| `./gradlew buildImage`          | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                             |
| `./gradlew runDocker`           | Run using the local docker image                                     |

Tests don't need `JWT_SECRET`: Ktor's test harness runs in development mode, where the app falls
back to an obviously worthless signing key rather than refusing to start.

### Confirming it started

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

```bash
curl http://localhost:8080/health   # -> OK
```

Two background jobs also log their state at startup — both disabled by default outside
`application.yaml`'s checked-in dev config:

```
Soft-delete purge job started with config=SoftDeletePurgeConfig(...)
Image blob reclamation job started with config=ImageBlobReclamationConfig(...)
```

(or `... is disabled`, if `cleanup.softDelete.enabled` / `imageBlob.reclamation.enabled` is
`false`). See [Recipe Image Architecture § Application Startup Changes](docs/recipe-image-architecture.md#application-startup-changes)
for what's new there and the `IMAGE_BLOB_STORAGE_ROOT` env var.

A normal run refuses to start outright when `JWT_SECRET` is missing. The one case that starts
anyway is Ktor development mode (`-Dio.ktor.development=true`), which logs `falling back to the
insecure development signing key` and issues tokens anyone can forge — fine for local
experimentation, never for an instance anyone else can reach.

### Database Setup

To reset and reseed the local database from scratch, run these scripts **in order** against the running Postgres container. `-v ON_ERROR_STOP=1` is not optional — see the warning below.

```bash
psql -h localhost -U postgres -d chefai_db -v ON_ERROR_STOP=1 -f src/main/resources/sql/drop_tables.sql -f src/main/resources/sql/create_tables.sql -f src/main/resources/sql/seed.sql -f src/main/resources/sql/seed_themealdb.sql
```

| Script | Purpose |
|--------|---------|
| `drop_tables.sql` | Drops all tables (clean slate) |
| `create_tables.sql` | Creates the full schema |
| `seed.sql` | Seeds reference data (allergens, ingredients, source classifications, tags, labels) + sample recipes and users |
| `seed_themealdb.sql` | Bulk recipe catalog (789 recipes). Generated by `./gradlew importTheMealDb` and checked in for a reproducible seed without a network dependency — regenerate and recommit it if the upstream catalog changes. **Requires `seed.sql` first**, which is why it's last in the command above. |

> **Order matters, and getting it wrong used to fail silently.** Every junction
> INSERT in `seed_themealdb.sql` is one multi-row statement referencing curated
> catalog UUIDs from `seed.sql`. Applied against a bare schema, each one aborts
> *in its entirety* on the first foreign-key violation — leaving recipes with no
> tags, no labels and no ingredients, while psql prints three ERROR lines and
> exits 0. That is how the database behind jmuci/ChefAI#182 ended up with 789
> ingredient-less recipes. The generated file now opens with a preflight `DO`
> block that raises instead, but keep `ON_ERROR_STOP=1` on anyway.

> The DB must be running first — `docker compose up db` if it isn't.

### Database-backed integration tests

`./gradlew dbIntegrationTest` runs every `Postgres*IntegrationTest`. Each one
**drops and recreates the `public` schema**, so it must never point at a
database whose contents you want to keep. The default target is `chefai_test`,
deliberately *not* the `chefai_db` used by `docker compose`:

```bash
psql -h localhost -U postgres -c "CREATE DATABASE chefai_test"
```

Override with `DB_URL` / `DB_USER` / `DB_PASSWORD`, as CI does.

## Postgres CheatSheet
# PostgreSQL psql Command Cheat Sheet

A quick reference of the most useful psql commands while developing with Postgres.

---

## 🔐 Connection & Exit

| Action | Command |
|--------|---------|
| Connect to a DB | `psql -U <user> -d <db>` |
| Connect to another DB (inside psql) | `\c <db>` |
| Quit psql | `\q` |
| Show connection info | `\conninfo` |

eg. ``` psql -h localhost -U postgres -d chefai_db ```
---

## 📚 List Databases, Tables, Schemas, Users

| Action | Command |
|--------|---------|
| List all databases | `\l` |
| List all tables in current schema | `\dt` |
| List all tables + system tables | `\dt+` |
| List schemas | `\dn` |
| List users / roles | `\du` |
| List indexes | `\di` |
| List functions | `\df` |

---

## 🧭 Inspecting Structures

| Action | Command |
|--------|---------|
| Describe table structure | `\d <table>` |
| Describe with details | `\d+ <table>` |
| Show all relations | `\d` |
| Show search path | `SHOW search_path;` |

---

## 📊 Query Display Options

| Action | Command |
|--------|---------|
| Toggle expanded mode | `\x` |
| Auto-expanded mode | `\x auto` |
| Show command history | `\s` |

---

## 🛠 Admin & Utility Commands

| Action | Command |
|--------|---------|
| Create database | `CREATE DATABASE name;` |
| Drop database | `DROP DATABASE name;` |
| Create user | `CREATE USER name WITH PASSWORD 'pass';` |
| Grant privileges | `GRANT ALL PRIVILEGES ON DATABASE db TO user;` |

---

## 📁 Import / Export Data

| Action | Command |
|--------|---------|
| Run SQL file | `psql -U user -d db -f file.sql` |
| Export table → CSV | `\copy table TO '/path/out.csv' CSV HEADER;` |
| Import CSV → table | `\copy table FROM '/path/in.csv' CSV HEADER;` |

---

## 🆘 Help & Reference

| Action | Command |
|--------|---------|
| List all psql meta-commands | `\?` |
| SQL command help | `\h` |
| Help for specific SQL command | `\h CREATE TABLE` |

---
