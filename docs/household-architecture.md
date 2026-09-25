# Household Architecture

> Status: data model, roles, invite lifecycle, all 15 HTTP endpoints, shared-meal-plan sync
> widening (visibility, removal tombstones, the recipe gap clause), and the shared grocery list
> are implemented and unit/integration-tested. See
> [`docs/sync-protocol.md`](sync-protocol.md#household-sharing-recipe-gap-clause) and its
> [Grocery List](sync-protocol.md#grocery-list) section for the sync-side details.

## What a household is

A household is a small group (family, flatshare) that shares meal plans and grocery lists.
Sharing is a **scope layer**, not an ownership migration: no existing row changes owner. A shared
meal plan carries a nullable `household_id`; everything else about it — including who created it —
is unchanged. Personal recipe libraries, bookmarks, collections, and recipe search results are
never shared.

A user belongs to **at most one household**, enforced at the database level (see below), not just
in service code.

## Data model

Three household tables, defined in `infrastructure/database/tables/` (a fourth,
`grocery_list_item_checks`, hangs off `meal_plans` rather than off a household directly — see
[`docs/sync-protocol.md`](sync-protocol.md#grocery-list)):

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
  trail and as the tombstone source that tells a removed member's client which household-scoped
  rows (meal plans, grocery items) to drop locally, driven by `server_removed_at` — see
  [`docs/sync-protocol.md`](sync-protocol.md#meal-plans).

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

All under `/api/v1/households`. `/sync/*` isn't a household endpoint itself — meal-plan and
grocery-list sharing widen the existing sync endpoints instead; see
[`docs/sync-protocol.md`](sync-protocol.md#meal-plans). Error body is the project's flat
`ErrorResponse(message: String)`; see
[`docs/exception-handling.md`](exception-handling.md#household-errors) for the exception → status
mapping `HouseholdRoutes.kt` applies. `HouseholdRoutes.kt` itself contains no business logic —
every check below happens in `HouseholdService`.

| Verb & Path | Auth | Success | Notes |
|---|---|---|---|
| `POST /households` | required | `201 HouseholdResponse` | |
| `GET /households/me` | required | `200 HouseholdResponse` | `404` if the caller has no household |
| `PATCH /households/{id}` | OWNER | `200 HouseholdResponse` | |
| `DELETE /households/{id}` | OWNER | `204` | Dissolves immediately, regardless of member count. Under the household row lock: removes every member, detaches every plan (`former_household_id` set), revokes outstanding invites |
| `GET /households/{id}/members` | member | `200 List<MemberResponse>` | ACTIVE members only |
| `DELETE /households/{id}/members/{userId}` | OWNER | `204` | `400` removing yourself or the owner |
| `POST /households/{id}/members/me/leave` | member | `204` | |
| `POST /households/{id}/invites` | OWNER | `201 CreateInviteResponse` | Rate-limited: 20/hour per caller. Body optional (empty = all defaults); a body that doesn't parse is `400`, never silently treated as `{}` |
| `GET /households/{id}/invites` | OWNER | `200 List<InviteSummaryResponse>` | Never includes the raw token |
| `DELETE /households/{id}/invites/{inviteId}` | OWNER | `204` | |
| `GET /households/invites/preview?token=` | optional | `200 InvitePreviewResponse` | Rate-limited: 30/10s per caller-or-IP |
| `POST /households/join` | required | `200 HouseholdResponse` | Rate-limited: 10/min per caller |
| `GET /households/invites/pending` | required | `200 List<InviteSummaryResponse>` | Invites addressed to the caller |
| `POST /households/invites/{inviteId}/accept` | required | `200 HouseholdResponse` | In-app accept, no token needed — **only for invites addressed to the caller** (`403` otherwise). Open link invites must go through `/join` with the token: an invite id is not a secret (it's logged and listed to the owner) |
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
  which avoids email-change edge cases. No matching user → `InviteeNotFoundException`. The lookup
  email is sanitized (trimmed, lowercased) the same way `AuthService.register`/`login` sanitize
  before storage, so inviting `Jane@Example.com` still resolves an account stored as
  `jane@example.com`.
- `GET /households/{id}/invites` filters by `HouseholdInvite.isUsable`, not just
  un-revoked/un-accepted — an invite that has since expired, or a multi-use invite that's hit its
  `max_uses` budget, no longer appears as "outstanding" even though nothing ever explicitly
  revoked or fully-consumed it in the narrower sense.

Accepting an invite (`joinByToken`, token path; `acceptInviteById`, in-app path from a pending-
invites inbox) shares one core implementation, `HouseholdRepository.acceptInvite`, which runs
**atomically** — a single transaction holding a row lock on the invite for its duration, not a
sequence of independently-committing steps:

1. Lock and re-fetch the invite row. Validity — not found, expired, revoked, or exhausted
   (`HouseholdInvite.isUsable`) — is checked **uniformly** against this freshly-locked read and all
   four map to the same `InviteNotFoundException`, so a caller probing invite ids or tokens can't
   distinguish "doesn't exist" from "expired" from "revoked" from "exhausted" (enumeration
   resistance). The lock also means a second, concurrent acceptor of the same single-use invite
   blocks here until the first transaction commits, then re-evaluates `isUsable` against the
   now-consumed row — without it, two different callers could each pass a stale `isUsable` check
   taken before either committed, and a single-use invite would silently admit two members.
2. If the invite names a specific `invitee_user_id` that isn't the caller →
   `InviteNotForCallerException`.
3. Insert the membership row. This happens **before** incrementing `use_count` — a losing racer
   against the one-household-per-user unique index rolls the whole transaction back, including the
   invite update below, before it ever consumes a reusable invite's budget, so no separate
   exhaustion bookkeeping is needed for that race.
4. Increment `use_count`; for single-use invites, stamp `accepted_by`/`accepted_at`.
5. Bump `server_updated_at` on every meal plan and grocery item already shared with the household
   (the "cursor backfill" — see [`docs/sync-protocol.md`](sync-protocol.md#cursor-backfill-on-join))
   so the newly joined member's next pull receives them regardless of how old their own cursor is.

Steps 3–5 committing as one transaction (rather than three separately-committing calls) is what
makes the whole accept atomic: a crash or exception partway through can no longer leave a member
added without the cursor backfill ever running, or an invite marked consumed without a membership
to show for it.

**Decline vs. revoke**: both share the `revoked_at` column rather than a separate `declined_at`.
They're distinguished by who initiated them — the invitee (`declineInvite`) vs. the household
owner (`revokeInvite`) — which the invite's `invitee_user_id` already makes unambiguous for audit
purposes, so a second column would be redundant.

## Leaving and removal

`leaveHousehold` (self-initiated) and `removeMember` (owner-initiated, on someone else) share one
core, `HouseholdRepository.departFromHousehold`, which runs the whole sequence **atomically** — one
transaction, holding a row lock on the household for its duration:

1. Mark the departing membership `REMOVED`, stamping both `removed_at` and `server_removed_at`.
2. Detach plans the departing member owns from the household: nulls `meal_plans.household_id` for
   plans they own (other members' plans are untouched) and stamps `former_household_id`/
   `household_detached_at` on the detached rows — what lets a still-ACTIVE household member (not
   just the departing member) be sent a synthetic removal tombstone for a plan they'd already
   cached — see `docs/sync-protocol.md`'s "Removal tombstones".
3. If the departing member was `OWNER` and other `ACTIVE` members remain, ownership transfers to
   the earliest-joined remaining member (updating both `households.owner_id` and the new owner's
   `household_members.role` in the same transaction).
4. If no `ACTIVE` members remain, the household dissolves: soft-deleted, with the departing
   member's own row (marked `REMOVED` in step 1) already covering every remaining membership, since
   by definition none are left.

Outcomes 3 and 4 are mutually exclusive by construction (4 requires zero remaining active members;
3 requires at least one). The household-row lock is what makes this safe against two members
leaving at once: without it, two concurrent departures could each act on a stale snapshot of "who's
left" and "was I the owner," potentially leaving `households.owner_id` pointing at a member who was
just removed by the other departure. Locking serializes the two calls into one after the other,
so the second always sees the first's completed result before deciding what to do.

`removeMember` additionally rejects: removing yourself (`HouseholdValidationException` — use
`leaveHousehold` instead) and removing the current owner (transfer ownership or delete the
household instead; in practice unreachable under normal invariants, since the owner is always the
caller when `requireOwner` has already passed, so guarded defensively rather than tested).

**Every plan reverts to personal, still owned by whoever created it — `meal_plans.user_id` is
never touched at any point in a household's life.** Zero data loss on any path. The same applies
to grocery items: they live and die with the meal plan they belong to, and a plan's
`household_id` reverting to personal is what stops both the departing member and any still-active
household member who'd cached that plan from being served its grocery items on their next pull
(surfaced as a removal tombstone for each, via two different mechanisms — see
`docs/sync-protocol.md`'s "Removal tombstones").

## See also

- [`docs/exception-handling.md`](exception-handling.md#household-errors) — `HouseholdException`
  hierarchy and its planned HTTP status mapping.
