# Household Architecture

> Status: data model, roles, invite lifecycle, all 15 HTTP endpoints, and shared-meal-plan sync
> widening (visibility, removal tombstones, the recipe gap clause) are implemented and
> unit/integration-tested — see [`docs/sync-protocol.md`](sync-protocol.md#household-sharing-recipe-gap-clause)
> for the sync-side details. Grocery lists have not started — see "What's deliberately deferred"
> below.

## What a household is

A household is a small group (family, flatshare) that shares meal plans and grocery lists.
Sharing is a **scope layer**, not an ownership migration: no existing row changes owner. A shared
meal plan carries a nullable `household_id`; everything else about it — including who created it —
is unchanged. Personal recipe libraries, bookmarks, collections, and recipe search results are
never shared.

A user belongs to **at most one household**, enforced at the database level (see below), not just
in service code.

## Data model

Three tables, defined in `infrastructure/database/tables/`:

- **`households`** (`HouseholdTable`) — `id`, `name`, `owner_id`, `created_at`, `updated_at`,
  `deleted_at`. `owner_id` is a **denormalized mirror** of whichever `household_members` row
  currently has `role = 'OWNER' AND status = 'ACTIVE'`. The membership row is authoritative — every
  ownership-transfer or dissolution path updates both in the same transaction, which is why
  `HouseholdService` owns all such transitions rather than leaving repository callers to keep them
  in sync by hand. `PostgresHouseholdRepositoryIntegrationTest` asserts this invariant holds after
  every mutation path (create, rename, transfer, dissolve, remove).

- **`household_members`** (`HouseholdMemberTable`) — `id`, `household_id`, `user_id`, `role`
  (`OWNER` | `MEMBER`), `status` (`ACTIVE` | `REMOVED`), `joined_at`, `removed_at`,
  `server_removed_at`. Rows are **never deleted**, only marked `REMOVED` — they double as the audit
  trail and, once the sync-widening PR lands, as the tombstone source that tells a removed member's
  client which household-scoped rows to drop locally (driven by `server_removed_at`).

  At most one **ACTIVE** row per `user_id`, enforced by a partial unique index —
  `idx_household_members_one_active_per_user ON household_members(user_id) WHERE status = 'ACTIVE'`.
  It must be partial: a plain unique index on `user_id` would permanently block a user from ever
  rejoining a household after leaving one, since their `REMOVED` row would still collide with a new
  `ACTIVE` row. Exposed's `Table.uniqueIndex()` can't express a filtered index, so this is
  hand-written DDL, added by `createHouseholdConstraintsIfMissing()` in `DatabaseInit.kt` (same
  category as the `recipes.search_vector` GIN index) and mirrored by hand in
  `sql/create_tables.sql`.

- **`household_invites`** (`HouseholdInviteTable`) — `id`, `household_id`, `created_by`,
  `token_hash`, `invitee_user_id`, `invitee_email`, `single_use`, `max_uses`, `use_count`,
  `expires_at`, `accepted_by`, `accepted_at`, `revoked_at`, `created_at`. See "Invite lifecycle"
  below.

## Roles and authorization

Two roles: `OWNER` and `MEMBER`. Only `OWNER` may invite, remove members, rename, or delete the
household. `HouseholdService` never trusts a client-supplied role — every mutating method funnels
through one of two private choke points that re-resolve the caller's own `household_members` row:

```kotlin
private suspend fun requireActiveMember(householdId: UUID, callerId: UUID): HouseholdMembership
private suspend fun requireOwner(householdId: UUID, callerId: UUID): HouseholdMembership
```

## Endpoints

All under `/api/v1/households` except `/sync/*` (not yet widened — see "What's deliberately
deferred"). Error body is the project's flat `ErrorResponse(message: String)`; see
[`docs/exception-handling.md`](exception-handling.md#household-errors) for the exception → status
mapping `HouseholdRoutes.kt` applies. `HouseholdRoutes.kt` itself contains no business logic —
every check below happens in `HouseholdService`.

| Verb & Path | Auth | Success | Notes |
|---|---|---|---|
| `POST /households` | required | `201 HouseholdResponse` | |
| `GET /households/me` | required | `200 HouseholdResponse` | `404` if the caller has no household |
| `PATCH /households/{id}` | OWNER | `200 HouseholdResponse` | |
| `DELETE /households/{id}` | OWNER | `204` | Dissolves immediately, regardless of member count |
| `GET /households/{id}/members` | member | `200 List<MemberResponse>` | ACTIVE members only |
| `DELETE /households/{id}/members/{userId}` | OWNER | `204` | `400` removing yourself or the owner |
| `POST /households/{id}/members/me/leave` | member | `204` | |
| `POST /households/{id}/invites` | OWNER | `201 CreateInviteResponse` | Rate-limited: 20/hour per caller |
| `GET /households/{id}/invites` | OWNER | `200 List<InviteSummaryResponse>` | Never includes the raw token |
| `DELETE /households/{id}/invites/{inviteId}` | OWNER | `204` | |
| `GET /households/invites/preview?token=` | optional | `200 InvitePreviewResponse` | Rate-limited: 30/10s per caller-or-IP |
| `POST /households/join` | required | `200 HouseholdResponse` | Rate-limited: 10/min per caller |
| `GET /households/invites/pending` | required | `200 List<InviteSummaryResponse>` | Invites addressed to the caller |
| `POST /households/invites/{inviteId}/accept` | required | `200 HouseholdResponse` | In-app accept, no token needed |
| `POST /households/invites/{inviteId}/decline` | required | `204` | Shares `revoked_at` with owner-revoke |

## One-household-per-user enforcement

Two layers, deliberately redundant:

1. **Service-level pre-check** (`createHousehold` only): reject up front if the caller already has
   an active household. Simple, but has a narrow TOCTOU race window.
2. **Database-level partial unique index** (all paths, including the race window above): the real
   enforcement. `PostgresHouseholdRepository.addMember` recognizes a unique-violation on
   `idx_household_members_one_active_per_user` by inspecting the `ExposedSQLException` message and
   translates it directly into `AlreadyInHouseholdException` — a normal domain exception that
   propagates through `HouseholdService` with no special-case handling needed there. Any other SQL
   exception is rethrown unchanged.

`PostgresHouseholdRepositoryIntegrationTest.secondActiveMembershipForTheSameUserFailsOnThePartialUniqueIndex`
proves the second of two attempts to make the same user active in two different households fails
cleanly rather than silently succeeding.

## Invite lifecycle

An invite is created by an `OWNER` (`HouseholdService.createInvite`):

- A raw, cryptographically random token is generated (`TokenHasher.generateSecureToken`, 256 bits)
  and returned to the caller **exactly once**. Only its SHA-256 hash
  (`TokenHasher.sha256Base64`) is persisted, in `token_hash` — the raw token is never stored
  anywhere, so a database read can never recover a usable token.
- `expiresInHours` is optional (defaults to 7 days) and server-clamped to a maximum of 30 days
  regardless of what the caller requests.
- The route layer builds the shareable `url` returned alongside the raw token as
  `"$inviteBaseUrl?token=$rawToken"`, where `inviteBaseUrl` comes from the `household.inviteBaseUrl`
  config key (`application.yaml`), defaulting to a placeholder if unset. **Override it in
  production** — this is not a real deployed domain.
- An email-addressed invite (`inviteeEmail` set) resolves to a concrete `users` row **at creation
  time** and stores `invitee_user_id` — not a bare email to match later. Authorization at accept
  time is always an id comparison (`InviteNotForCallerException`), never a string comparison,
  which avoids email-change edge cases. No matching user → `InviteeNotFoundException`.

Accepting an invite (`joinByToken`, token path; `acceptInviteById`, in-app path from a pending-
invites inbox) shares one core implementation, in this order:

1. Look up the invite. Validity — not found, expired, revoked, or exhausted
   (`HouseholdInvite.isUsable`) — is checked **uniformly** and all four map to the same
   `InviteNotFoundException`, so a caller probing invite ids or tokens can't distinguish "doesn't
   exist" from "expired" from "revoked" from "exhausted" (enumeration resistance).
2. If the invite names a specific `invitee_user_id` that isn't the caller →
   `InviteNotForCallerException`.
3. Insert the membership row. This happens **before** incrementing `use_count` — a losing racer
   against the one-household-per-user unique index rolls the whole operation back before it ever
   consumes a reusable invite's budget, so no separate exhaustion bookkeeping is needed for that
   race.
4. Increment `use_count`; for single-use invites, stamp `accepted_by`/`accepted_at`.
5. Bump `server_updated_at` on every row the newly joined member should immediately see (the
   "cursor backfill" — see `docs/sync-protocol.md` once the sync-widening PR lands). Currently a
   no-op: it bumps `meal_plans` and `grocery_list_item_checks`, neither of which has household
   linkage yet.

**Decline vs. revoke**: both share the `revoked_at` column rather than a separate `declined_at`.
They're distinguished by who initiated them — the invitee (`declineInvite`) vs. the household
owner (`revokeInvite`) — which the invite's `invitee_user_id` already makes unambiguous for audit
purposes, so a second column would be redundant.

## Leaving and removal

`leaveHousehold` (self-initiated) and `removeMember` (owner-initiated, on someone else) share one
core, `departFromHousehold`:

1. Mark the departing membership `REMOVED`, stamping both `removed_at` and `server_removed_at`.
2. Detach plans the departing member owns from the household (`detachPlansOwnedBy`) — nulls
   `meal_plans.household_id` for plans they own; other members' plans are untouched (they simply
   lose access, surfaced via the removal tombstone — see `docs/sync-protocol.md`).
3. If the departing member was `OWNER` and other `ACTIVE` members remain, ownership transfers to
   the earliest-joined remaining member (`transferOwnership`, updating both `households.owner_id`
   and the new owner's `household_members.role` in one call).
4. If no `ACTIVE` members remain, the household dissolves (`dissolveHousehold`): soft-deleted, and
   every remaining membership row marked `REMOVED` with a fresh `server_removed_at` — not just the
   member who triggered it, so every departed member's client eventually gets a tombstone.

Outcomes 3 and 4 are mutually exclusive by construction (4 requires zero remaining active members;
3 requires at least one).

`removeMember` additionally rejects: removing yourself (`HouseholdValidationException` — use
`leaveHousehold` instead) and removing the current owner (transfer ownership or delete the
household instead; in practice unreachable under normal invariants, since the owner is always the
caller when `requireOwner` has already passed, so guarded defensively rather than tested).

**Every plan reverts to personal, still owned by whoever created it — `meal_plans.user_id` is
never touched at any point in a household's life.** Zero data loss on any path.

## What's deliberately deferred

Grocery lists haven't started — the table doesn't exist yet, and two spots stay correct-but-inert
placeholders until it does:

| Method | Waits on |
|---|---|
| `HouseholdRepository.bumpServerUpdatedAtForHouseholdRows`'s grocery half (the meal-plan half is live) | `grocery_list_item_checks` |
| `SyncRepository.processGroceryListItems` | `grocery_list_item_checks`, `SyncGroceryListItem` |

`bumpServerUpdatedAtForHouseholdRows` already bumps `meal_plans` on join (see
`docs/sync-protocol.md`'s "Cursor Backfill on Join") — only its grocery-row half is pending.

## See also

- [`docs/exception-handling.md`](exception-handling.md#household-errors) — `HouseholdException`
  hierarchy and its planned HTTP status mapping.
