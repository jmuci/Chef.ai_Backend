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
| [Recipe Search](docs/recipe-search.md) | Postgres full-text recipe search — query sanitization, ranking, rate limiting |
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

## Test Users

| Email          | Password  |
|----------------|-----------|
| test1@ex.com   | test123!  |
| test2@ex.com   | test123!  |
| test3@ex.com   | test123!  |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                        | Description                                                                |
|---------------------------------------------|----------------------------------------------------------------------------|
| `./gradlew test`                            | Run the tests                                                              |
| `./gradlew build`                           | Build everything                                                           |
| `buildFatJar`                               | Build an executable JAR of the server with all dependencies included       |
| `buildImage`                                | Build the docker image to use with the fat JAR                             |
| `publishImageToLocalRegistry`               | Publish the docker image locally                                           |
| `run`                                       | Run the server                                                             |
| `runDocker`                                 | Run using the local docker image                                           |
| ` docker compose -f docker-compose.yaml up` | Run with docker compose which will also start and connect to a Postgres Db |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
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

**NOTE**: The service requires the DB to be running (see [next section](#docker-compose))

### Docker Compose
To start only the DB (as you might want to start the service on the IDE for debugging):
```bash
docker compose up db
```
Add -d for detached mode. 

```bash
docker compose down db
```
To start both the DB and the service, run: 
```bash
 docker compose -f docker-compose.yaml up --build
```

### Database Setup

To reset and reseed the local database from scratch, run these three scripts in order against the running Postgres container:

```bash
psql -h localhost -U postgres -d chefai_db -f src/main/resources/sql/drop_tables.sql -f src/main/resources/sql/create_tables.sql -f src/main/resources/sql/seed.sql
```

| Script | Purpose |
|--------|---------|
| `drop_tables.sql` | Drops all tables (clean slate) |
| `create_tables.sql` | Creates the full schema |
| `seed.sql` | Seeds reference data (allergens, ingredients, source classifications) + sample recipes and users |

> The DB must be running first — `docker compose up db` if it isn't.

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
