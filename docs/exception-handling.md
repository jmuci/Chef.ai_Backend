# Exception-Based Error Handling Migration

## Overview

Avoid nullable return types and use exception-based error handling, providing:

- **Type-safe error handling**: Each error type is explicit
- **Better error messages**: Specific exception types with descriptive messages
- **Clearer HTTP mappings**: Direct mapping from exceptions to HTTP status codes
- **No null checks**: Eliminates nullable return types that obscure error conditions

## New Exception Hierarchy

```kotlin
sealed class AuthException(message: String) : Exception(message)

├── ValidationException              // 400 Bad Request
├── UserAlreadyExistsException      // 409 Conflict
├── InvalidCredentialsException      // 401 Unauthorized
├── UserNotFoundException            // 401 Unauthorized (for refresh token)
├── InvalidRefreshTokenException     // 401 Unauthorized
├── TokenReuseDetectedException      // 401 Unauthorized + revoke all tokens
└── AuthInternalException            // 500 Internal Server Error
```


## HTTP Status Code Mapping

| Exception | HTTP Status | Response Code |
|-----------|-------------|---------------|
| `ValidationException` | 400 Bad Request | Client should fix input |
| `UserAlreadyExistsException` | 409 Conflict | Email already registered |
| `InvalidCredentialsException` | 401 Unauthorized | Wrong email/password |
| `InvalidRefreshTokenException` | 401 Unauthorized | Token invalid/expired |
| `TokenReuseDetectedException` | 401 Unauthorized | Security breach detected |
| `UserNotFoundException` | 401 Unauthorized | User doesn't exist |
| `AuthInternalException` | 500 Internal Server Error | Server-side failure |

## Route Handler Pattern

```kotlin
post("/register") {
    try {
        val request = call.receive<RegisterRequest>()
        val response = authService.register(request)
        call.respond(HttpStatusCode.Created, response)
    } catch (ex: ValidationException) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(ex.message))
    } catch (ex: UserAlreadyExistsException) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse(ex.message))
    } catch (ex: AuthInternalException) {
        call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
    } catch (ex: Exception) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request format"))
    }
}
```

## Example Usage

### Registration with Validation Error

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "invalid", "username": "user", "password": "pass"}'

# Response: 400 Bad Request
{
  "message": "Email format invalid"
}
```

### Duplicate Registration

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "existing@example.com", "username": "user", "password": "ValidPass123"}'

# Response: 409 Conflict
{
  "message": "User with email existing@example.com already exists"
}
```

### Invalid Credentials

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "wrong"}'

# Response: 401 Unauthorized
{
  "message": "Invalid email or password"
}
```

### Token Reuse Detection

```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "ALREADY_USED_TOKEN"}'

# Response: 401 Unauthorized
{
  "message": "Token reuse detected. All sessions have been terminated for security."
}
```

---

## Sync Error Codes

Unlike auth errors (which map to HTTP status codes), sync errors are returned **inside the 200 response body** as per-item lists. There are two categories:

### Recipe sync errors (`SyncErrors`)

Returned in `SyncPushResponse.errors[]`:

| Code | Meaning | Client action |
|------|---------|---------------|
| `INVALID_UUID` | Recipe UUID is not a valid UUID | Fix client-side ID generation |
| `INVALID_CREATOR` | `creatorId` is not a valid UUID | Fix client-side ID |
| `CREATOR_MISMATCH` | `creatorId` does not match authenticated user | Auth mismatch — re-authenticate |
| `INVALID_PRIVACY` | `privacy` is not `PUBLIC` or `PRIVATE` | Fix enum value |
| `INVALID_INGREDIENT` | An `ingredientId` is not a valid UUID | Fix client-side data |
| `INGREDIENT_NOT_FOUND` | An `ingredientId` does not exist in the catalogue | Remove or replace the ingredient |
| `INVALID_TAG` | A `tagId` is not a valid UUID | Fix client-side data |
| `INVALID_LABEL` | A `labelId` is not a valid UUID | Fix client-side data |

### Bookmark sync errors (`BookmarkErrors`)

Returned in `SyncPushResponse.bookmarkErrors[]`:

| Code | Meaning | Client action |
|------|---------|---------------|
| `USER_MISMATCH` | `userId` does not match the authenticated user | Re-authenticate; don't push other users' bookmarks |
| `INVALID_RECIPE_ID` | `recipeId` is not a valid UUID | Fix client-side ID |
| `RECIPE_NOT_FOUND` | Recipe does not exist or is PRIVATE and not owned by the user | Remove the bookmark locally; recipe is inaccessible |

Bookmark errors are **per-item** — other bookmarks and all recipes in the same push batch are unaffected.

---

## Sync Push/Pull Transport Errors

Separate from the per-item codes above: these apply to the *whole request*, before it ever
reaches `SyncService` — a malformed body or bad query param, not a problem with any individual
recipe/bookmark.

| Status | Trigger |
|--------|---------|
| `401` | Missing/invalid JWT |
| `400` | Body isn't valid JSON, or is missing a required field (e.g. `recipes`) |
| `400` | `/sync/pull`: `since` missing/non-numeric, or `limit <= 0` |
| `409` | `ExposedSQLException` — a DB constraint conflict outside the normal per-recipe conflict path |
| `500` | Unexpected server/DB failure |

**Gotcha this route learned the hard way**: `call.receive<SyncPushRequest>()` does not throw
`JsonConvertException` or `kotlinx.serialization.SerializationException` directly on a malformed
body — Ktor's `ContentNegotiation` plugin wraps those in `io.ktor.server.plugins.BadRequestException`
first. `SyncRoutes.kt`'s catch block originally only matched the two inner types, so every
malformed/incomplete push body fell through to the generic `catch (ex: Exception)` and answered
`500 Internal Server Error` instead of `400 Bad Request`. Fixed by adding an explicit
`catch (ex: BadRequestException)` ahead of the (now effectively dead, but kept for any future
in-block throw) `JsonConvertException`/`SerializationException` clauses. If you add a new route
that calls `call.receive<T>()` inside a multi-branch try/catch like this one, catch
`BadRequestException` — don't rely on `JsonConvertException` alone.

---

## Sync Pagination Diagnostics

Not a client-facing error — the response is still `200 OK`. This is a server log signal for
operators debugging a `/sync/pull` account that isn't making forward progress.

`SyncService.fetchStablePage` logs a `WARN` when it can't produce a page without splitting a
tied `server_updated_at` boundary:

```
Sync pull for user <userId>: recipe tie at server_updated_at=<millis> exceeds the
5000-row safety fetch — cursor may not advance past it until the tie clears
```

| Field | Meaning |
|-------|---------|
| `user <userId>` | The authenticated user whose pull triggered the log |
| `server_updated_at=<millis>` | The shared millisecond timestamp the tie sits on — also what `serverTimestamp` will equal in the response |
| `5000-row safety fetch` | `TIE_ESCAPE_FETCH_LIMIT` in `SyncService.kt` |

**When this fires**: more than 5,000 recipes share the exact same millisecond-precision
`server_updated_at`, starting at the client's own `since` cursor — e.g. a bulk import that
wrote that many rows inside one millisecond. Ordinary single- or batch-recipe pushes can't
trigger it: each recipe upsert does real per-row DB I/O, so genuine ties are expected to be far
smaller than the safety fetch. See the cursor stability guarantee in
[`docs/sync-protocol.md`](sync-protocol.md#cursor-stability-guarantee) for why a tie has to be
kept whole rather than split across pages in the first place.

**Client impact**: `hasMore` stays `true` and `serverTimestamp` stops advancing past the tied
value until it's resolved server-side — the same symptom the underlying cursor-freeze bug
produced, but now scoped to a documented, logged, and narrow edge case instead of any account
whose data exceeded `limit`.

**Fix**: re-run the offending bulk write with `server_updated_at` spread across distinct
milliseconds, or raise `TIE_ESCAPE_FETCH_LIMIT` if 5,000 is genuinely too small for a
legitimate one-off import. Resolving this class of tie completely (regardless of size) would
need a compound `(timestamp, id)` cursor, which is a sync protocol change, not just a backend
fix.

---

## Recipe Image Errors

Unlike sync errors, `PUT`/`GET`/`DELETE /recipes/{id}/image` map directly to HTTP status codes —
closer to the auth pattern than the sync one. Full validation order and rationale:
[`docs/recipe-image-architecture.md`](recipe-image-architecture.md#error-states-reference).

| Status | Trigger | Body |
|--------|---------|------|
| `401` | Missing/invalid JWT | `ErrorResponse` |
| `400` | `recipeId` path segment isn't a valid UUID | `ErrorResponse` |
| `404` | Recipe doesn't exist, isn't owned by caller (upload/clear), or isn't visible (serve) | `ErrorResponse` (upload/clear) or empty (serve) |
| `413` | Body exceeds `maxUploadBytes` | `ErrorResponse` |
| `415` | `Content-Type` isn't `image/{jpeg,png,webp}`, or magic bytes don't match the declared type | `ErrorResponse` |
| `422` | `sha256(body)` doesn't match `X-Content-SHA256` | `ErrorResponse` |
| `403` | Scraped-image upload while `imageBlob.allowScrapedImageUpload=false` | `{"error":"SCRAPED_UPLOAD_DISABLED"}` |
| `403` | Upload would exceed `imageBlob.perUserQuotaBytes` | `{"error":"QUOTA_EXCEEDED"}` |
| `429` | Per-user upload rate limit exceeded | Ktor `RateLimit` default body |

The two `403` variants use a stable machine-readable `{"error": "<CODE>"}` shape rather than the
usual free-text message, because the client branches on them specifically —
`SCRAPED_UPLOAD_DISABLED` is treated as permanent (stop retrying that recipe's image upload),
while everything else is retried. Every "not visible to this caller" case on `GET`/`PUT`/`DELETE`
returns `404`, never `403`, so the endpoint can't be used to probe whether a recipe exists.

---

## Future Enhancements

- Custom exception handler plugin for consistent error responses
- Structured error codes for client-side error handling
- Error correlation IDs for debugging
- Internationalized error messages
