# Backend Prompt: Households — Shared Meal Plans & Grocery Lists

> Generated: September 2026
> Companion client prompt: `docs/prompts/households-android-prompt.md`
> Target repo: `ktor-chefai` (Kotlin + Ktor + PostgreSQL + Exposed DSL + JWT)

---

## Context

ChefAI users are individuals today. Every row is user-scoped: `recipes.creator_id`,
`meal_plans.user_id`, and every sync query filters `= userId`. We want a household — a family or
flatshare — where **all members see and edit the same meal plan and its grocery list**.

Sharing is implemented as a **scope layer**, not an ownership migration. No existing row changes
owner. Three new tables, one nullable `meal_plans.household_id`, and a widening of exactly two read
paths.

### What is shared

| Shared | Not shared |
|---|---|
| Meal plans carrying a `household_id` | Personal recipe libraries |
| Those plans' days and grocery-list state | Bookmarks / collections |
| Recipes **assigned to a shared plan's days** (read-only visibility, via the gap clause) | Recipe search results |
| | Recipe images beyond existing visibility rules |

### Product decisions (settled — do not redesign)

1. A user belongs to **at most one household**, enforced by a DB constraint, not just service code.
2. Roles are `OWNER | MEMBER`. Only `OWNER` may invite, remove members, rename, or delete.
3. **No email sending.** No SMTP/SES/SendGrid dependency. Invites are links plus an in-app inbox.
4. Only authenticated users may create or join a household (the app is anonymous-first; anonymous
   sessions are simply out of scope here).

---

## 0. Client contract requirements — read this first

These five were found by diffing this spec against the Android plan. They are **not optional**;
each one breaks the client at compile or run time if missed.

### 0.1 `SyncMealPlanDto` must carry `ownerId`

The client currently stamps `userId = <whoever pulled the plan>` because until now you only ever
pulled your own plans. The first shared plan a member pulls would silently reassign ownership on
their device. The DTO must be self-describing:

```kotlin
@Serializable
data class SyncMealPlanDto(
    val uuid: String,
    val ownerId: String,        // NEW — the plan's real owner, never the caller
    val householdId: String?,   // NEW — null = personal, unchanged behaviour
    val name: String,
    val status: String,
    val preferencesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val days: List<SyncMealPlanDayDto>,
)
```

On **push**, do not infer ownership from the JWT caller for a plan the caller may edit but does not
own. Persist `ownerId` as-is after verifying the caller is allowed to write that plan.

### 0.2 Pulled `creators` must union recipe creators **and** meal-plan owners

`pullRecipes` currently derives `creatorIds` purely from the recipe page. The client upserts
`creators` before meal plans to satisfy a local FK on the owner id. A household member who owns a
shared plan but authored none of the recipes on that page will be missing → Room FK constraint
failure on the client's first shared pull.

```kotlin
val creatorIds = page.map { it.recipe.creatorId }.toSet() +
    mealPlans.map { it.plan.ownerId }.toSet()   // NEW
```

### 0.3 Grocery item wire shape

Use an explicit `checked: Boolean`, **not** a tombstone-on-uncheck. This keeps an uncheck an
ordinary last-writer-wins update and stops the uncheck/re-check cycle accumulating `DELETED` rows.
`deletedAt` means only "this item left the list".

```kotlin
@Serializable
data class SyncGroceryListItem(
    val mealPlanId: String,
    val itemKey: String,        // client-derived, opaque; validate length <= 256
    val checked: Boolean,
    val checkedBy: String?,
    val updatedAt: Long,        // client logical clock, LWW comparand
    val deletedAt: Long?,
)
```

Wire field name is `groceryListItems` on push request, push response and pull response.

### 0.4 Accept-by-invite-id endpoint

Invite listings deliberately withhold raw tokens (correct). But `POST /households/join` takes only
`{token}`, so an invite discovered **in-app** can never be accepted. Add:

```
POST /api/v1/households/invites/{inviteId}/accept
```

Authorized by `invitee_user_id == caller.userId`. Keep `/join` for the link path. Do **not** relax
the listing to return tokens.

### 0.5 Decline endpoint

The client's pending-invite card offers Accept and Decline. Owner-side revoke is a different action
by a different person. Add:

```
POST /api/v1/households/invites/{inviteId}/decline
```

Same authorization as accept. Sets `revoked_at` (or a dedicated `declined_at` if you prefer the
audit distinction — pick one and document it).

### 0.6 Also settled

- **Departing member's own plan:** on leave *and* on remove, null `household_id` for plans owned by
  the departing member, in the same transaction as the membership update. Otherwise a plan owned by
  someone who left stays visible to the household they left.
- **Join backfill is server-side only.** The client does *not* reset its cursor. Your
  `server_updated_at` bump is the sole mechanism.

---

## 1. Database Schema

No migration runner exists in this project. Schema comes from
`SchemaUtils.createMissingTablesAndColumns` in `DatabaseInit.kt`, plus hand-maintained SQL in
`src/main/resources/sql/`. **All three must be kept in sync by hand.**

```sql
-- ============ HOUSEHOLDS ============
CREATE TABLE IF NOT EXISTS households (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    owner_id    UUID NOT NULL REFERENCES users(uuid) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_households_owner_id ON households(owner_id);

-- ============ HOUSEHOLD_MEMBERS ============
CREATE TABLE IF NOT EXISTS household_members (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id      UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
    role              TEXT NOT NULL CHECK (role IN ('OWNER','MEMBER')),
    status            TEXT NOT NULL CHECK (status IN ('ACTIVE','REMOVED')),
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at        TIMESTAMPTZ NULL,
    server_removed_at TIMESTAMPTZ NULL
);

-- At most one ACTIVE household per user. MUST be partial: REMOVED rows are retained as the
-- tombstone source, and a plain unique index would wrongly block ever rejoining a household.
-- Exposed cannot express a filtered index — hand-written, same category as the tsvector index.
CREATE UNIQUE INDEX IF NOT EXISTS idx_household_members_one_active_per_user
    ON household_members(user_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_household_members_household_id
    ON household_members(household_id);
CREATE INDEX IF NOT EXISTS idx_household_members_server_removed_at
    ON household_members(server_removed_at) WHERE server_removed_at IS NOT NULL;

-- ============ HOUSEHOLD_INVITES ============
CREATE TABLE IF NOT EXISTS household_invites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    created_by      UUID NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,   -- sha256(raw token); raw token NEVER stored
    invitee_user_id UUID NULL REFERENCES users(uuid) ON DELETE CASCADE,
    invitee_email   TEXT NULL,              -- denormalized, display/audit only
    single_use      BOOLEAN NOT NULL DEFAULT TRUE,
    max_uses        INTEGER NULL,
    use_count       INTEGER NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_by     UUID NULL REFERENCES users(uuid) ON DELETE SET NULL,
    accepted_at     TIMESTAMPTZ NULL,
    revoked_at      TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_household_invites_household_id
    ON household_invites(household_id);
CREATE INDEX IF NOT EXISTS idx_household_invites_invitee_user_id
    ON household_invites(invitee_user_id) WHERE invitee_user_id IS NOT NULL;

-- ============ MEAL_PLANS (altered) ============
ALTER TABLE meal_plans ADD COLUMN IF NOT EXISTS household_id UUID NULL
    REFERENCES households(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_meal_plans_household_id
    ON meal_plans(household_id) WHERE household_id IS NOT NULL;

-- ============ GROCERY_LIST_ITEM_CHECKS ============
CREATE TABLE IF NOT EXISTS grocery_list_item_checks (
    meal_plan_id      UUID NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    item_key          TEXT NOT NULL,
    checked           BOOLEAN NOT NULL,
    checked_by        UUID NULL REFERENCES users(uuid) ON DELETE SET NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT NULL,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (meal_plan_id, item_key)
);
CREATE INDEX IF NOT EXISTS idx_grocery_list_item_checks_server_updated_at
    ON grocery_list_item_checks(server_updated_at);
```

**Pre-existing staleness to fix while you're here:** `sql/create_tables.sql` has no `meal_plans`,
`meal_plan_days` or `user_preferences` blocks at all — they exist only via Exposed. Backfill them
(with `household_id` included from the start) and mirror the drops into `drop_tables.sql`.

`households.owner_id` is a denormalized mirror of the `household_members` row with
`role='OWNER' AND status='ACTIVE'`. The membership row is authoritative. **Every ownership-transfer
and dissolution path must update both in one transaction** — add an integration test asserting the
invariant holds after every mutation path.

---

## 2. Exposed Table Objects

`src/main/kotlin/infrastructure/database/tables/`

```kotlin
object HouseholdTable : UUIDTable("households", "id") {
    val name = text("name")
    val owner_id = reference("owner_id", UserTable, onDelete = ReferenceOption.RESTRICT)
    val created_at = timestamp("created_at").clientDefault { Clock.System.now() }
    val updated_at = timestamp("updated_at").clientDefault { Clock.System.now() }
    val deleted_at = timestamp("deleted_at").nullable()
}

object HouseholdMemberTable : UUIDTable("household_members", "id") {
    val household_id = reference("household_id", HouseholdTable, onDelete = ReferenceOption.CASCADE).index()
    val user_id = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val role = text("role")
    val status = text("status")
    val joined_at = timestamp("joined_at").clientDefault { Clock.System.now() }
    val removed_at = timestamp("removed_at").nullable()
    val server_removed_at = timestamp("server_removed_at").nullable()
    // Partial unique index (status='ACTIVE') is hand-written — see DatabaseInit.kt
}

object HouseholdInviteTable : UUIDTable("household_invites", "id") {
    val household_id = reference("household_id", HouseholdTable, onDelete = ReferenceOption.CASCADE).index()
    val created_by = reference("created_by", UserTable, onDelete = ReferenceOption.CASCADE)
    val token_hash = varchar("token_hash", 255).uniqueIndex()
    val invitee_user_id = optReference("invitee_user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val invitee_email = text("invitee_email").nullable()
    val single_use = bool("single_use").default(true)
    val max_uses = integer("max_uses").nullable()
    val use_count = integer("use_count").default(0)
    val expires_at = timestamp("expires_at")
    val accepted_by = optReference("accepted_by", UserTable, onDelete = ReferenceOption.SET_NULL)
    val accepted_at = timestamp("accepted_at").nullable()
    val revoked_at = timestamp("revoked_at").nullable()
    val created_at = timestamp("created_at").clientDefault { Clock.System.now() }
}

object GroceryListItemCheckTable : Table("grocery_list_item_checks") {
    val meal_plan_id = reference("meal_plan_id", MealPlanTable, onDelete = ReferenceOption.CASCADE)
    val item_key = text("item_key")
    val checked = bool("checked")
    val checked_by = optReference("checked_by", UserTable, onDelete = ReferenceOption.SET_NULL)
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at").index()
    override val primaryKey = PrimaryKey(meal_plan_id, item_key)
}

// MealPlanTable.kt — add one line:
val household_id = optReference("household_id", HouseholdTable, onDelete = ReferenceOption.SET_NULL).index()
```

Register the three new tables in `DatabaseInit.initDatabaseAndSchema()`, and add
`createHouseholdConstraintsIfMissing()` for the partial unique index — same shape as the existing
`createRecipeSearchIndexIfMissing()`, called from the same place, idempotent via `IF NOT EXISTS`.

---

## 3. Domain Layer

`src/main/kotlin/domain/model/Household.kt` — pure Kotlin, zero framework deps:

```kotlin
enum class HouseholdRole { OWNER, MEMBER }
enum class HouseholdMemberStatus { ACTIVE, REMOVED }

data class Household(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val members: List<HouseholdMembership>,
)

data class HouseholdMembership(
    val householdId: UUID,
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String,
    val role: HouseholdRole,
    val status: HouseholdMemberStatus,
    val joinedAt: Instant,
)

data class HouseholdInvite(
    val id: UUID,
    val householdId: UUID,
    val createdBy: UUID,
    val inviteeUserId: UUID?,
    val inviteeEmail: String?,
    val singleUse: Boolean,
    val maxUses: Int?,
    val useCount: Int,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val revokedAt: Instant?,
)

data class NewHouseholdInvite(
    val householdId: UUID,
    val createdBy: UUID,
    val tokenHash: String,
    val inviteeUserId: UUID?,
    val inviteeEmail: String?,
    val singleUse: Boolean,
    val maxUses: Int?,
    val expiresAt: Instant,
)
```

`src/main/kotlin/domain/repository/HouseholdRepository.kt`:

```kotlin
interface HouseholdRepository {
    suspend fun createHousehold(name: String, ownerId: UUID): Household
    suspend fun getHousehold(id: UUID): Household?
    suspend fun getActiveHouseholdForUser(userId: UUID): Household?
    suspend fun getActiveMembership(householdId: UUID, userId: UUID): HouseholdMembership?
    suspend fun listActiveMembers(householdId: UUID): List<HouseholdMembership>
    suspend fun renameHousehold(householdId: UUID, name: String)
    suspend fun addMember(householdId: UUID, userId: UUID, role: HouseholdRole): HouseholdMembership
    suspend fun removeMember(householdId: UUID, userId: UUID, at: Instant)
    suspend fun transferOwnership(householdId: UUID, newOwnerId: UUID)
    suspend fun dissolveHousehold(householdId: UUID, at: Instant)

    suspend fun createInvite(invite: NewHouseholdInvite): HouseholdInvite
    suspend fun findInviteByTokenHash(tokenHash: String): HouseholdInvite?
    suspend fun getInvite(inviteId: UUID): HouseholdInvite?
    suspend fun listOutstandingInvites(householdId: UUID): List<HouseholdInvite>
    suspend fun listPendingInvitesForUser(userId: UUID): List<HouseholdInvite>
    suspend fun revokeInvite(inviteId: UUID)
    suspend fun recordInviteAcceptance(inviteId: UUID, acceptedBy: UUID, at: Instant)

    /** Cursor backfill on join — see section 6. Bumps meal_plans AND grocery_list_item_checks. */
    suspend fun bumpServerUpdatedAtForHouseholdRows(householdId: UUID, at: Instant)

    /** Leave/remove cleanup — nulls household_id on plans owned by the departing member. */
    suspend fun detachPlansOwnedBy(householdId: UUID, userId: UUID)
}
```

`src/main/kotlin/domain/exception/HouseholdExceptions.kt` — sealed `HouseholdException` with
`HouseholdNotFoundException`, `NotHouseholdOwnerException`, `NotHouseholdMemberException`,
`AlreadyInHouseholdException`, `InviteNotFoundException`, `InviteNotForCallerException`,
`InviteeNotFoundException`.

`src/main/kotlin/domain/util/TokenHasher.kt` — extract the duplicated logic currently in
`AuthService.hashRefreshToken` and `JwtService.generateRefreshToken`:

```kotlin
object TokenHasher {
    fun generateSecureToken(byteLength: Int = 32): String   // SecureRandom + URL-safe base64, no padding
    fun sha256Base64(token: String): String
}
```

---

## 4. Service Rules (`domain/service/HouseholdService.kt`)

All business rules live here, **not** in routes. Two choke points, used by every mutating method:

```kotlin
private suspend fun requireOwner(householdId: UUID, callerId: UUID): HouseholdMembership
private suspend fun requireActiveMember(householdId: UUID, callerId: UUID): HouseholdMembership
```

Never trust a client-supplied role. Always resolve the caller's own `household_members` row.

### Create household
Reject if the caller already has an `ACTIVE` membership → `AlreadyInHouseholdException`. Insert
household + `OWNER` membership in one transaction.

### Accept invite (token path and invite-id path share this core)
In one transaction, in this order:
1. Look up the invite (`token_hash` or `id`). Not found / expired / revoked / exhausted →
   `InviteNotFoundException` (uniform, for enumeration resistance).
2. If `invitee_user_id != null && invitee_user_id != caller` → `InviteNotForCallerException`.
3. Insert the `household_members` row. **Do this before incrementing `use_count`** — a losing racer
   hits the partial unique index and the whole transaction rolls back before consuming a reusable
   invite's budget. No special-case code needed.
4. Map a unique-violation on the index to the same `409 ALREADY_IN_HOUSEHOLD` the pre-check returns.
5. Increment `use_count`; set `accepted_by`/`accepted_at` when `single_use`.
6. Call `bumpServerUpdatedAtForHouseholdRows(householdId, now)` — the cursor backfill.

### Leave / remove member
In one transaction:
1. Mark the membership `REMOVED`, set `removed_at` and `server_removed_at = now`.
2. `detachPlansOwnedBy(householdId, departingUserId)` — nulls `household_id` on their own plans.
3. If the departing member was `OWNER` and other `ACTIVE` members remain → `transferOwnership` to
   the earliest `joined_at` remaining member, updating `households.owner_id` too.
4. If no `ACTIVE` members remain → `dissolveHousehold`: soft-delete the household, mark all
   memberships `REMOVED` (each gets a `server_removed_at`, driving the client tombstones), and
   `UPDATE meal_plans SET household_id = NULL WHERE household_id = :id`.

Every plan reverts to personal, owned by its original `meal_plans.user_id`, which is never touched
at any point in a household's life. **Zero data loss in every path.**

### Invite creation
- `TokenHasher.generateSecureToken(32)` → 256-bit token. Return the raw token exactly once; persist
  only `sha256`.
- `expires_at` mandatory, server-clamped to a max of 30 days.
- Email path: resolve the email to a `users` row at creation time and store `invitee_user_id`.
  No such user → `404 INVITEE_NOT_FOUND`. Authorization at accept time compares
  `invitee_user_id`, **never** an email string (avoids email-change edge cases).

---

## 5. API Endpoints

All under `/api/v1` except `/sync/*`, which keeps its existing un-prefixed mount. Error body is the
project's real flat `ErrorResponse(message: String)` — note `docs/exception-handling.md` documents an
aspirational nested shape that no route actually uses; follow the code, and reconcile the doc
separately.

| Verb & Path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| `POST /api/v1/households` | required | `{name}` | `201 HouseholdResponse` | `400` blank name; `409 ALREADY_IN_HOUSEHOLD` |
| `GET /api/v1/households/me` | required | — | `200 HouseholdResponse` | `404 NO_HOUSEHOLD` |
| `PATCH /api/v1/households/{id}` | OWNER | `{name}` | `200 HouseholdResponse` | `400`; `403 NOT_OWNER`; `404` |
| `DELETE /api/v1/households/{id}` | OWNER | — | `204` | `403 NOT_OWNER`; `404` |
| `GET /api/v1/households/{id}/members` | member | — | `200 List<MemberResponse>` | `403 NOT_MEMBER`; `404` |
| `DELETE /api/v1/households/{id}/members/{userId}` | OWNER | — | `204` | `400` self/owner; `403`; `404` |
| `POST /api/v1/households/{id}/members/me/leave` | member | — | `204` | `403 NOT_MEMBER`; `404` |
| `POST /api/v1/households/{id}/invites` | OWNER | `{inviteeEmail?, singleUse=true, maxUses?, expiresInHours?}` | `201 CreateInviteResponse {token, url, expiresAt, singleUse, maxUses}` | `400`; `403`; `404 INVITEE_NOT_FOUND` |
| `GET /api/v1/households/{id}/invites` | OWNER | — | `200 List<InviteSummaryResponse>` (no raw tokens) | `403`; `404` |
| `DELETE /api/v1/households/{id}/invites/{inviteId}` | OWNER | — | `204` | `403`; `404` |
| `GET /api/v1/households/invites/preview?token=` | optional | — | `200 InvitePreviewResponse {householdName, inviterDisplayName}` | `404 INVITE_NOT_FOUND` |
| `POST /api/v1/households/join` | required | `{token}` | `200 HouseholdResponse` | `404`; `403 INVITE_NOT_FOR_YOU`; `409 ALREADY_IN_HOUSEHOLD` |
| `GET /api/v1/households/invites/pending` | required | — | `200 List<InviteSummaryResponse>` | — |
| `POST /api/v1/households/invites/{inviteId}/accept` | required | — | `200 HouseholdResponse` | `403`; `404`; `409` |
| `POST /api/v1/households/invites/{inviteId}/decline` | required | — | `204` | `403`; `404` |

`GET .../invites/preview` is optional-auth so a link opened while logged out can show *something*
before forcing sign-in. It returns household name and inviter display name only.

### Rate limits
Register in `Routing.kt`'s `install(RateLimit)` block alongside the existing buckets:

- `HOUSEHOLD_INVITE_CREATE_RATE_LIMIT_NAME` — 20/hour, keyed by `call.userId`
- `HOUSEHOLD_JOIN_RATE_LIMIT_NAME` — 10/min, keyed by `call.userId`
- `HOUSEHOLD_INVITE_PREVIEW_RATE_LIMIT_NAME` — 30/10s, keyed by `call.userId ?: remoteAddress`
  (same shape as the existing `RECIPE_DETAIL_RATE_LIMIT_NAME`)

---

## 6. Sync Protocol Changes

### 6.1 Meal plans — widened visibility

`SyncRepository.getMealPlanForUser(uuid, userId)` becomes `getMealPlanForMember(uuid, userId)`:

```
WHERE id = :uuid AND (user_id = :userId OR household_id = <caller's active household id>)
```

Membership is single-valued, so resolve the caller's active household with one lookup. Keep it a
private helper inside `PostgresSyncRepository` — this class already reaches into other tables
directly (see `findCandidateRecipeIds`).

`findDeltaMealPlans(userId, sinceMillis)` widens to:

```
WHERE server_updated_at > :since
  AND (user_id = :userId OR household_id = :activeHouseholdId)
```

Update the call site in `MealPlanGenerationService.startGeneration` too — any active member can now
regenerate a shared plan, which is intentional.

### 6.2 Removal tombstones

Reuse the **existing** `SyncMealPlanDto.deletedAt` field; invent no new wire shape. When serving
`userId`, additionally check: does `household_members` have a row for this user with
`status='REMOVED' AND server_removed_at > :since`? If so, synthesize
`deletedAt = server_removed_at` for every row that belonged to that household.

This value is **per-caller and synthetic** — the underlying row's real `deleted_at` stays null for
everyone still in the household. Same treatment for grocery items.

### 6.3 Recipe visibility — the gap clause

Private helper:

```sql
SELECT DISTINCT dinner_recipe_id, lunch_recipe_id
FROM meal_plan_days d
JOIN meal_plans p ON d.meal_plan_id = p.id
WHERE p.household_id = :activeHouseholdId AND p.deleted_at IS NULL
```

Extend `findDeltaRecipes`'s `where{}` from
`(server_updated_at > since) AND (creator_id = userId OR privacy = 'PUBLIC')`
by adding `OR (id inList householdVisibleRecipeIds)` as a **gap** clause — unconditional on
`server_updated_at`, exactly the union idiom `collectReferenceData`'s helpers already use.

Why this placement matters: `pullRecipes` derives ingredient/tag/label/creator ids strictly from
whatever lands in `page`. Putting the clause inside `findDeltaRecipes` means reference data covers
the newly visible recipes automatically, with no second code path to forget. This is the project's
#1 stated red flag, and this placement is what defuses it.

Also extend `SyncService.getRecipeDetail`'s visibility check to allow household-visible recipes.
Needs a new thin `SyncRepository.isRecipeHouseholdVisible(userId, recipeId): Boolean`.

**Explicitly do NOT touch:** `isRecipeAccessibleBy` (the bookmark push gate),
`findCandidateRecipeIds` (search / generation candidates), `RecipeSearchService`,
`RecipeSearchRoutes`. Widening those is a separate product decision. Add a negative test proving a
household-mate's unrelated private recipe stays invisible.

### 6.4 Grocery list

New `SyncService.processGroceryListItems(userId, request)`, mirroring `processMealPlans`:

1. Validate `mealPlanId` parses; `itemKey` non-blank and ≤ 256 chars.
2. **Authorization choke point:** `getMealPlanForMember(mealPlanId, userId) != null`. Without this,
   any authenticated user could write checks against an arbitrary plan id.
3. LWW: `existing.serverUpdatedAtMillis > item.updatedAt` → conflict; else upsert.

Call it from `pushRecipes` / `pullRecipes` alongside the existing `processBookmarks` /
`processMealPlans` calls.

```kotlin
@Serializable enum class GroceryItemErrors(val message: String) {
    INVALID_MEAL_PLAN_ID("mealPlanId is not a valid UUID"),
    MEAL_PLAN_NOT_ACCESSIBLE("meal plan does not exist or caller cannot edit it"),
    INVALID_ITEM_KEY("itemKey must be non-blank and at most 256 characters")
}
```

### 6.5 Cursor backfill on join

`bumpServerUpdatedAtForHouseholdRows` runs, inside the accept transaction:

```sql
UPDATE meal_plans SET server_updated_at = :now WHERE household_id = :id;
UPDATE grocery_list_item_checks SET server_updated_at = :now
  WHERE meal_plan_id IN (SELECT id FROM meal_plans WHERE household_id = :id);
```

**Tradeoff, for the record:** this is O(rows under the household) write amplification per join, and
transiently re-delivers already-synced rows to existing members (harmless idempotent upserts). The
alternative — a per-source cursor floor — is more surgical but requires threading a second
`since` value through every widened delta query, a far larger blast radius. For households of 2–6
people this is the right call. Document it in `docs/sync-protocol.md`.

Note the referenced *recipes* need no bump: they arrive via the gap clause, which ignores the cursor
by construction.

---

## 7. PR Sequence

Each PR is independently reviewable and leaves `./gradlew test` green.

| PR | Scope |
|---|---|
| **B1** | Tables, `HouseholdRepository` + Postgres impl, `HouseholdService`, `TokenHasher`, exceptions, `DatabaseInit` registration + partial index, SQL file updates. No HTTP surface. |
| **B2** | `HouseholdDtos`, `HouseholdRoutes`, rate-limit buckets, `Routing.kt` + `Application.kt` wiring. All 15 endpoints from section 5. |
| **B3** | `meal_plans.household_id`, `getMealPlanForMember`, widened `findDeltaMealPlans`, removal tombstones, `ownerId` + `householdId` on `SyncMealPlanDto`, `creators` union (§0.2), household-reassignment rule. |
| **B4** | Recipe gap clause in `findDeltaRecipes`, `getRecipeDetail` widening, `isRecipeHouseholdVisible`. |
| **B5** | `grocery_list_item_checks`, `SyncGroceryListItem`, `processGroceryListItems`, push/pull wiring. |
| **B6** | Optional: fold `AuthService`/`JwtService` onto `TokenHasher`; finish docs; `ktlintFormat`. |

Ship **B3 and B4 together** — B4 alone is meaningless, and B3 alone gives members a plan full of
recipes they cannot resolve.

---

## 8. Testing Requirements

Non-negotiable per project rules: unit tests mock repositories, integration tests use in-memory
fakes, Testcontainers for real-Postgres repository tests. Both must pass.

**`domain/service/HouseholdServiceTest.kt`** — one-household enforcement; owner-only invite/remove;
auto-transfer on owner leave; dissolve on last member leave; invite accept happy path plus expired /
revoked / exhausted / wrong-invitee / already-in-household.

**`PostgresHouseholdRepositoryIntegrationTest.kt`** — **must** include: two concurrent `addMember`
calls for the same user, asserting the second fails on the partial unique index rather than silently
succeeding; and the `households.owner_id` ↔ `household_members` OWNER invariant after every
mutation path.

**`SyncServiceTest.kt`** — shared-plan push by a non-creator member accepted; push with a foreign
`householdId` rejected; pull tombstones a removed member's shared plans but not an active member's;
pull includes a household-mate's old, pre-cursor PRIVATE recipe referenced by a shared plan **plus
its full reference data**; pull excludes a household-mate's unreferenced private recipe; grocery LWW
with two members toggling the same item.

**`PostgresSyncRepositoryIntegrationTest.kt`** — the join backfill regression: a member joins after
a plan already exists with an old `server_updated_at`, then pulls with a cursor *newer* than that
plan's original stamp. They must receive the plan.

**`HouseholdRoutesIntegrationTest.kt`** — the full endpoint table, using the existing
`module(householdRepository = FakeHouseholdRepository())` DI-override pattern.

Update `FakeSyncRepository` with household-membership simulation, widened
`getMealPlanForMember`, tombstone synthesis and grocery storage.

---

## 9. Docs to Update

Mandated by project rules:

- **New** `docs/household-architecture.md` — data model, role/ownership rules, invite lifecycle and
  security posture, endpoint table.
- `docs/sync-protocol.md` — `householdId`/`ownerId` on meal plans; the widened visibility predicate;
  a new "Household Membership Changes & Cursor Behavior" subsection covering the join bump and
  removal tombstones; a "Grocery List" section mirroring the existing "Bookmarks" structure; an
  explicit callout that bookmarks and search remain unwidened.
- `docs/auth-architecture.md` — add household routes to the route-tier list.
- `docs/exception-handling.md` — `HouseholdException` and `GroceryItemErrors` tables.
- `README.md` — doc index.

---

## 10. Definition of Done

- [ ] All five client-contract items in §0 implemented exactly as specified
- [ ] `./gradlew test` green
- [ ] A user cannot belong to two households, proven by a concurrent-insert test
- [ ] A non-member cannot read or write another household's plan or grocery items (IDOR tests)
- [ ] A newly joined member receives a pre-existing shared plan on their next pull
- [ ] A removed member receives tombstones and stops receiving household rows
- [ ] Leaving never destroys data — every plan reverts to personal, owned by its creator
- [ ] Raw invite tokens appear in exactly one response and are never persisted
- [ ] Bookmarks, recipe search and meal-plan candidate selection are provably unchanged
- [ ] Docs in §9 updated
