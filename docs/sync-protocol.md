# Synchronization Protocol

## Overview

The Chef AI sync system is a **cursor-based, last-write-wins protocol** designed for mobile clients to maintain offline-first recipe data while handling multi-device conflicts gracefully. The protocol ensures:

- **Offline-first**: Clients operate independently; sync is eventual
- **Referential integrity**: FK constraints never violated on client upserts
- **Minimal round-trips**: Conflict responses include all reference data needed for resolution
- **Efficient deltas**: Only changed entities sent; gap entities (old but referenced) included to prevent FK violations

---

## Core Concepts

### Cursor-Based Sync
Clients track a **server timestamp cursor** (`since`) representing their last successful sync point. Each server response includes a new cursor (`serverTimestamp`), advancing the client to the latest server state.

```
Client's last sync: since = 500ms
Server state: recipes changed after 500ms exist
Client pull(since=500) → receives all recipes where server_updated_at > 500ms
→ client advances cursor to max(server_updated_at) from response
```

### Delta vs. Gap Entities

The sync system fetches entities using a **union pattern**:

```sql
WHERE server_updated_at > :since          -- Delta: changed since cursor
   OR uuid IN (:referencedIds)            -- Gap: old but referenced by current page
```

**Delta Entities**: New or changed since the cursor. Client *must* receive them to stay current.

```
Recipe "Pasta" (created at T=100ms, unchanged)
Recipe "Carbonara" (updated at T=1200ms) ← delta since cursor=500ms
→ Client gets "Carbonara" via delta
```

**Gap Entities**: Unchanged since cursor but referenced by entities in the current page. Client *may* already have them, but we send them to prevent FK constraint failures.

```
Tag "Quick" (created at T=50ms, never updated)
Recipe "Carbonara" (T=1200ms) references Tag "Quick"
→ Query: WHERE server_updated_at > 500ms OR uuid = "Quick"
→ Client gets "Quick" via gap clause (FK safety)
```

### Reference Data

Every recipe references foreign keys:

```
Recipe
  ├─ ingredients[] → Ingredient FK
  │   ├─ allergenId → Allergen FK
  │   └─ sourcePrimaryId → SourceClassification FK
  ├─ tagIds[] → Tag FK
  └─ labelIds[] → Label FK
```

The sync response includes **all** referenced entities, computed via the union pattern. This ensures Room/SQLite on Android never encounters a missing FK when upserting a recipe.

---

## Endpoints

### GET /sync/pull?since={ms}&limit={n}

Fetches **delta recipes and their reference data** since the client's cursor.

**Request**:
```
GET /sync/pull?since=500&limit=20 HTTP/1.1
Authorization: Bearer <token>
```

**Response** (`SyncPullResponse`):
```json
{
  "recipes": [
    {
      "uuid": "abc123",
      "title": "Carbonara",
      "creatorId": "user-uuid",
      "ingredients": [{"ingredientId": "egg-uuid", "quantity": 3, "unit": "eggs"}],
      "tagIds": ["quick-uuid"],
      "updatedAt": 1200,
      "..."
    }
  ],
  "ingredients": [
    {
      "uuid": "egg-uuid",
      "displayName": "Eggs",
      "allergenId": "shellfish-uuid",
      "updatedAt": 100,
      "..."
    }
  ],
  "allergens": [
    {
      "uuid": "shellfish-uuid",
      "displayName": "Shellfish",
      "updatedAt": 50,
      "..."
    }
  ],
  "tags": [
    {
      "uuid": "quick-uuid",
      "displayName": "Quick",
      "updatedAt": 100,
      "..."
    }
  ],
  "labels": [...],
  "sourceClassifications": [...],
  "serverTimestamp": 1200,
  "hasMore": false
}
```

**Key Behavior**:
- `recipes`: All recipes where `creatorId == userId OR privacy == PUBLIC` AND `server_updated_at > since`
- `ingredients`, `tags`, `labels`, `allergens`, `sourceClassifications`: Union of delta + gap entities
  - **Delta**: `server_updated_at > since`
  - **Gap**: Referenced by recipes in the current page but old (allows FK resolution)
- `serverTimestamp`: Client's new cursor = `max(server_updated_at)` from response (or `since` if empty)
- `hasMore`: True if query found more rows than `limit` (client should paginate)

**Why Reference Data?**
Without the union pattern, consider this:
1. Tag "Quick" created at T=50ms, never updated
2. Recipe "Carbonara" created at T=1200ms with Tag "Quick"
3. Client pulls with `since=500ms`
   - ❌ Old approach: Only returns delta ingredients (T > 500) → missing Tag "Quick"
   - ❌ Room's FK constraint on `recipe_tags(tagId)` fails → sync roll-back
   - ✅ Union pattern: Returns Tag "Quick" via gap clause → upsert succeeds

---

### POST /sync/push

Pushes **dirty client recipes** to the server. Server processes each recipe independently, returning accepted, conflicts, and errors.

**Request** (`SyncPushRequest`):
```json
{
  "recipes": [
    {
      "uuid": "pasta-uuid",
      "title": "Pasta Aglio e Olio",
      "creatorId": "device-user-uuid",
      "updatedAt": 1500,
      "ingredients": [...],
      "tagIds": [...],
      "..."
    }
  ]
}
```

**Response** (`SyncPushResponse`):
```json
{
  "accepted": [
    {
      "uuid": "pasta-uuid",
      "serverUpdatedAt": 1510
    }
  ],
  "conflicts": [
    {
      "uuid": "carbonara-uuid",
      "reason": "SERVER_NEWER",
      "serverVersion": {
        "uuid": "carbonara-uuid",
        "title": "Carbonara (Original)",
        "updatedAt": 1300,
        "ingredients": [...],
        "..."
      }
    }
  ],
  "errors": [
    {
      "uuid": "invalid-uuid",
      "reason": "INGREDIENT_NOT_FOUND",
      "message": "Ingredient does not exist"
    }
  ],
  "serverTimestamp": 1510,
  "referenceData": {
    "ingredients": [...],
    "tags": [...],
    "labels": [...],
    "allergens": [...],
    "sourceClassifications": [...]
  }
}
```

**Per-Recipe Processing**:
1. **Validation**: UUID format, creator match, ingredient/tag existence, privacy enum
2. **Conflict Check**: `existing.serverUpdatedAtMillis > recipe.updatedAt` → conflict
3. **Accept/Conflict Decision**:
   - ✅ **Accepted**: Upsert recipe, store `serverUpdatedAtMillis = Clock.System.now()`
   - ⚠️ **Conflict**: Return `serverVersion` (current server state) + reference data
   - ❌ **Error**: Skip recipe, record validation error

**Reference Data in Conflicts** (`referenceData` field):
- Computed from *all conflict serverVersions* combined
- Uses same union pattern, but `sinceMillis = null` (gap-only, no cursor)
- Enables client to apply server's version without a separate pull
- Includes transitive FKs (allergens/source-classifications from returned ingredients)

---

## Multi-Device Conflict Resolution: Full Example

### Scenario: Three Recipes, Two Devices, One Conflict

**Initial State (T=100)**:
```
Server DB:
  Recipe "Pasta" (id=r1, creatorId=alice, updatedAt=100, server_ts=100)
  Tag "Quick" (id=t1, updatedAt=50)
  Ingredient "Garlic" (id=i1, updatedAt=80)
```

```
Device B (Alice's Phone):
  Last sync: since=0
  ↓ pull(since=0, limit=100)
  ← Pasta (r1) + Garlic (i1) + Quick (t1)
  → Cursor = 100
```

### Step 1: Device A Edits (T=500)

**Device A (Alice's Tablet)** → Edits "Pasta" locally → `updatedAt=500`
```
Device A: Pasta (r1, "Pasta Carbonara", updatedAt=500)
```

**Device A → POST /sync/push**
```json
{
  "recipes": [
    {"uuid": "r1", "title": "Pasta Carbonara", "updatedAt": 500, ...}
  ]
}
```

**Server Processing** (T=501):
- Conflict check: `100 > 500`? No → Accept
- Upsert: `serverUpdatedAtMillis = 501`
- Response:
```json
{
  "accepted": [{"uuid": "r1", "serverUpdatedAt": 501}],
  "conflicts": [],
  "serverTimestamp": 501
}
```

```
Server DB (after Device A's push):
  Recipe "Pasta Carbonara" (id=r1, updatedAt=500, server_ts=501) ← CHANGED
  Tag "Quick" (id=t1, updatedAt=50)
  Ingredient "Garlic" (id=i1, updatedAt=80)
```

### Step 2: Device B Edits (Offline, Unaware of Device A's Change)

**Device B (Offline)** → Edits "Pasta" locally → `updatedAt=300` (before Device A's change)
```
Device B: Pasta (r1, "Pasta Primavera", updatedAt=300)
```

```
Timeline:
  T=0     ← Device B's cursor
  T=100   ← Server had "Pasta" (original version)
  T=300   ← Device B edits "Pasta" offline (unaware of T=500 change)
  T=500   ← Device A edits "Pasta"
  T=501   ← Device A syncs (accepted)
  T=502   ← Device B comes online, tries to push
```

**Device B → POST /sync/push** (T=502)
```json
{
  "recipes": [
    {"uuid": "r1", "title": "Pasta Primavera", "updatedAt": 300, ...}
  ]
}
```

**Server Processing** (T=502):
- Conflict check: `501 > 300`? **Yes** → Conflict!
- Return `serverVersion` (current: "Pasta Carbonara") + reference data for resolution
- Response:

```json
{
  "accepted": [],
  "conflicts": [
    {
      "uuid": "r1",
      "reason": "SERVER_NEWER",
      "serverVersion": {
        "uuid": "r1",
        "title": "Pasta Carbonara",
        "updatedAt": 500,
        "ingredients": [{"ingredientId": "i1", ...}],
        "tagIds": ["t1"],
        "..."
      }
    }
  ],
  "referenceData": {
    "ingredients": [
      {"uuid": "i1", "displayName": "Garlic", "updatedAt": 80, ...}
    ],
    "tags": [
      {"uuid": "t1", "displayName": "Quick", "updatedAt": 50, ...}
    ],
    "labels": [],
    "allergens": [],
    "sourceClassifications": []
  }
}
```

### Step 3: Device B Resolves the Conflict

**User Interaction** (on Device B):
```
⚠️ Conflict: Server has "Pasta Carbonara" (edited by other device)
📋 Options:
   [Keep Local "Pasta Primavera"]
   [Accept Server "Pasta Carbonara"] ← User chooses this
   [Merge] ← (not implemented in this protocol)
```

**Device B Resolution** → Accepts server's "Pasta Carbonara":
```
Device B: Take serverVersion from conflict response
         Update updatedAt = serverUpdatedAt + 1 = 501 + 1 = 502
         Re-push as new truth
```

**Device B → POST /sync/push** (T=503)
```json
{
  "recipes": [
    {
      "uuid": "r1",
      "title": "Pasta Carbonara",
      "updatedAt": 502,
      "ingredients": [{"ingredientId": "i1", ...}],
      "tagIds": ["t1"],
      "..."
    }
  ]
}
```

**Server Processing** (T=503):
- Conflict check: `501 > 502`? No → Accept
- Upsert: `serverUpdatedAtMillis = 503`
- Response:

```json
{
  "accepted": [{"uuid": "r1", "serverUpdatedAt": 503}],
  "conflicts": [],
  "serverTimestamp": 503
}
```

```
Server DB (after Device B's resolution):
  Recipe "Pasta Carbonara" (id=r1, updatedAt=502, server_ts=503)
    ↑ Same title (Device B accepted Device A's version)
    ↑ But with Device B's resolution timestamp
```

### Step 4: Device B Syncs from New Cursor

**Device B → GET /sync/pull?since=100&limit=100** (T=504)

Now that Device B's cursor is old (100), it will see the conflict resolution:

```json
{
  "recipes": [
    {
      "uuid": "r1",
      "title": "Pasta Carbonara",
      "updatedAt": 502,
      "ingredients": [...],
      "tagIds": ["t1"],
      "..."
    }
  ],
  "ingredients": [
    {"uuid": "i1", "displayName": "Garlic", "updatedAt": 80, ...}
  ],
  "tags": [
    {"uuid": "t1", "displayName": "Quick", "updatedAt": 50, ...}
  ],
  "serverTimestamp": 503,
  "hasMore": false
}
```

**Device B UI Update**:
```
✅ "Pasta Carbonara" (synced)
   Last edited: Device A, on another device
   Your edits: Replaced with server version
```

---

## Why This Works: The FK Safety Contract

### Problem
Mobile apps (Android Room) have strict FK constraints. If a recipe references an ingredient that doesn't exist, the entire transaction fails:

```sql
INSERT INTO recipes VALUES (...);
INSERT INTO recipe_ingredients VALUES (recipeId, ingredientId); ← FAIL if ingredientId missing
→ Entire sync transaction rolls back
```

### Solution: Union Pattern

The server response includes **every referenced entity**, using the union of:

1. **Delta** (changed since cursor): Ensures client gets all updates
2. **Gap** (old but referenced): Ensures every FK target exists on client

**Example: Ingredient Missing via Delta-Only Approach**
```
Tag "Quick" created at T=50, never updated
Recipe "Carbonara" created at T=1200, references Tag "Quick"
Client pulls with since=500

❌ Delta-only: WHERE server_updated_at > 500
   → Gets Recipe "Carbonara" (T=1200) but NOT Tag "Quick" (T=50)
   → FK constraint fails on client: tagId not in tags table

✅ Union pattern: WHERE server_updated_at > 500 OR uuid IN (referenced)
   → Gets Recipe "Carbonara" (T=1200) ✓
   → Gets Tag "Quick" (T=50 < 500, but referenced, so via gap) ✓
   → FK constraint succeeds
```

### Transitive Dependencies

Ingredients can have their own FKs to allergens and source classifications. The response includes these:

```
Query for ingredients (by union):
  → Returns ingredients via delta + gap

For each returned ingredient:
  → Extract allergenId, sourcePrimaryId
  → Query allergens by union (delta + gap)
  → Query sourceClassifications by union (delta + gap)

Result: Complete FK chain, no missing references
```

---

## Pagination

For large datasets, clients can paginate using the cursor:

```
Iteration 1: GET /sync/pull?since=0&limit=20
  ← recipes: [r1, r2, ..., r20], hasMore=true, serverTimestamp=400

Iteration 2: GET /sync/pull?since=400&limit=20
  ← recipes: [r21, r22, ..., r40], hasMore=true, serverTimestamp=600

Iteration 3: GET /sync/pull?since=600&limit=20
  ← recipes: [r41], hasMore=false, serverTimestamp=700
```

Each page's reference data is independently complete:
- Page 1 includes all tags/ingredients/etc. referenced by r1..r20
- Page 2 includes all tags/ingredients/etc. referenced by r21..r40 (possibly overlapping with Page 1)
- Client deduplicates and merges across pages

---

## Validation & Errors

The server validates each pushed recipe:

| Check | Error Reason | Behavior |
|-------|--------------|----------|
| UUID format valid? | `INVALID_UUID` | Skip recipe, record error |
| creatorId matches auth user? | `CREATOR_MISMATCH` | Skip recipe, record error |
| Privacy enum valid? | `INVALID_PRIVACY` | Skip recipe, record error |
| Ingredients exist in catalogue? | `INGREDIENT_NOT_FOUND` | Skip recipe, record error |
| Tag UUIDs well-formed? | `INVALID_TAG` | Skip recipe, record error |
| Tag UUIDs in database? | (silently ignored) | Accept; unknown tags are allowed |

Errors are **per-recipe** and don't fail the entire push request. Client receives all three lists: accepted, conflicts, errors.

---

## Server Implementation Notes

### Atomicity
- Each recipe's decision (accept/conflict/error) is independent
- No cross-recipe failures
- All recipes in a push are processed atomically as a batch (single transaction for sync)

### Clock
- Server uses `Clock.System.now()` for `serverUpdatedAtMillis`
- In Kotlin/Ktor: epoch milliseconds, monotonically increasing per machine
- Client assumes server timestamps are ordered: push(T1) before push(T2) → serverTs(T1) < serverTs(T2)

### Reference Data Isolation
- Pull response: `sinceMillis` non-null → delta + gap
- Push conflict response: `sinceMillis = null` → gap only (no cursor context during push)
- Same `collectReferenceData` function handles both via optional parameter

---

## Client Responsibilities

While the server ensures referential integrity, the client must:

1. **Maintain cursor**: Persist `serverTimestamp` from responses
2. **Conflict UI**: Present conflict to user with server version + reference data
3. **Deduplicate**: Across paginated pulls, merge reference data (some entities appear in multiple pages)
4. **FK ordering**: Insert entities in order (allergens → sourceClassifications → ingredients → tags → labels → recipes)
5. **Validation**: Reject local edits that violate constraints before pushing

---

## Summary Table

| Aspect | Details |
|--------|---------|
| **Sync Model** | Cursor-based, eventual consistency, last-write-wins |
| **Conflict Resolution** | User chooses server or local; server version included in response |
| **Reference Safety** | Union pattern (delta + gap) ensures every FK target in response |
| **Transitive FKs** | Allergens/source-classifications derived from returned ingredients |
| **Round-trips** | Conflict response includes all FK deps; no extra pull needed |
| **Pagination** | Cursor-based; each page's reference data is independent |
| **Atomicity** | Batch; all recipes processed in single transaction |
| **Errors** | Per-recipe; don't fail batch |
