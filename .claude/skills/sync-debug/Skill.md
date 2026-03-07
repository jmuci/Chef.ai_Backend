---
name: ChefAI Sync Debug
description: Debug offline-first sync in ChefAI: dual-timestamp traps, push/pull semantics, syncState column, soft delete lifecycle, and pagination cursor issues.
---

## Overview

ChefAI uses an offline-first sync protocol. The Android client pushes local changes and pulls server changes by cursor. Two timestamps exist for every entity — getting them confused is the most common source of sync bugs.

---

## Timestamp Reference — Know These Cold

| Column | Type | Set By | Purpose |
|--------|------|--------|---------|
| `updated_at` | `Long` (millis) | **Client** | Client's last-modified time. Not trustworthy for ordering across devices. |
| `server_updated_at` | `Instant` (TIMESTAMPTZ) | **Server** | Set on every write. Indexed. Used as sync cursor. Authoritative. |
| `deleted_at` | `Instant?` | **Server** | Set on soft delete. Null = active. Non-null = soft-deleted. |

**UserTable exception**: uses a single `Instant updated_at` — no `server_updated_at`, no `deleted_at`. Don't apply sync patterns to users.

### Common Timestamp Trap

If sync pull returns stale data:
- Check whether the query filters on `server_updated_at` (correct) vs `updated_at` (wrong)
- Confirm the index on `server_updated_at` is being used (check query plan if needed)
- Check the `since` parameter type — client sends millis (Long), server compares to `Instant`

---

## Sync Endpoints

### POST /sync/push

Request: `SyncPushRequest { recipes: List<RecipeSyncItem> }`
Response: `SyncPushResponse { accepted: Int, conflicts: List<ConflictItem>, errors: List<ErrorItem> }`

**What the server does:**
1. For each recipe in the push batch:
   - If `deleted_at` set on item → soft delete: set `deleted_at = now`, `server_updated_at = now`
   - If recipe exists and server's `server_updated_at > client's updatedAt` → conflict (server wins)
   - Otherwise → upsert, set `server_updated_at = Clock.System.now()`
2. Update junction table rows (`RecipeIngredientTable`, `RecipeTagTable`, `RecipeLabelTable`) and set their `syncState` column

**Conflict definition:** Server record was modified more recently than the client's version of the record.

### GET /sync/pull?since=\<ms\>&limit=100

- `since`: millis timestamp (Long); compare to `server_updated_at`
- Returns: all recipes where `server_updated_at > since`, ordered by `server_updated_at ASC`
- Includes soft-deleted records (client needs to know to remove them locally)
- Response includes `hasMore: Boolean` and `serverTimestamp: Long` (use as next `since`)

**Cursor pagination pattern:**
```
First pull:  since=0,         limit=100 → returns 100 records, hasMore=true, serverTimestamp=T1
Second pull: since=T1,        limit=100 → continues from T1
Until:       hasMore=false    → sync complete
```

---

## Junction Table syncState

`RecipeIngredientTable`, `RecipeTagTable`, `RecipeLabelTable` each have a `syncState` text column.

Valid states (confirm against current enum/constants in codebase):
- `PENDING` — client has local change not yet pushed
- `SYNCED` — server confirmed this row
- `CONFLICT` — server rejected this version

**Caution**: These junction tables use camelCase column names (`recipeId`, `ingredientId`) — a legacy inconsistency. The entity tables use snake_case. Don't add new camelCase columns.

---

## Soft Delete Lifecycle

```
Client delete → POST /sync/push with deletedAt set
             → Server sets deleted_at = now, server_updated_at = now
             → Included in next /sync/pull responses (deleted_at non-null)
             → Client removes from local DB on receiving
             → After 90 days: SoftDeletePurgeService hard-deletes
```

**SoftDeletePurgeService** (background job):
- Runs on configurable schedule (default: daily)
- Config: `cleanup.softDelete.enabled`, `retentionDays` (default 90), `intervalHours`, `batchSize`, `dryRun`
- Only hard-deletes if `deleted_at` is older than `retentionDays`
- Scope: `CoroutineScope(SupervisorJob() + Dispatchers.Default)` in `Application.kt`

---

## Known Bug

**`publicRecipes()` in `PostgresRecipesRepository`** queries the Privacy column for the string `"public"` but the Kotlin `Privacy` enum value is `PUBLIC`. The fix is to compare against `Privacy.PUBLIC.name` (which is `"PUBLIC"`).

---

## Debugging Steps

### Sync returns no records when records exist
1. Check `since` value — is it millis? Is it being compared to `server_updated_at` (Instant)?
2. Check `server_updated_at` is being set on every write (upsert path, soft delete path)
3. Verify the record's `deleted_at` is null if it should be active

### Sync returns records already seen (duplicates)
1. `serverTimestamp` in response must be the max `server_updated_at` of returned records, not `Clock.System.now()`
2. Client must use `serverTimestamp` (not its own clock) as the next `since`

### Conflicts not detected
1. Confirm the comparison is `server_updated_at > clientUpdatedAt` — both sides must be in comparable units
2. `server_updated_at` is Instant; `updated_at` client value is Long millis — convert correctly

### Push accepted but pull doesn't return the record
1. Confirm `server_updated_at` was written during the push handler (not just `updated_at`)
2. Check that `withContext(Dispatchers.IO)` wraps the transaction (avoid thread-pool issues)

---

## Key Files

| Path | Purpose |
|------|---------|
| `domain/service/SyncService.kt` | Push/pull business logic |
| `presentation/routes/SyncRoutes.kt` | Route handlers for /sync/push and /sync/pull |
| `infrastructure/database/tables/RecipeTable.kt` | Dual-timestamp columns |
| `infrastructure/database/tables/RecipeIngredientTable.kt` | syncState column |
| `domain/service/SoftDeletePurgeService.kt` | Background purge job |
| `application/dto/SyncDto.kt` | `SyncPushRequest`, `SyncPushResponse`, `SyncPullResponse` |
| `docs/sync-protocol.md` | Full protocol spec (in project docs/) |
