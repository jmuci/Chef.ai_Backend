---
name: ChefAI Auth Flow
description: JWT auth, BCrypt passwords, refresh token rotation, and reuse detection in the ChefAI Ktor backend. Load when working on auth routes, tokens, or security.
---

## Overview

ChefAI uses JWT (HS256, 1h) + opaque refresh tokens. All auth logic lives in `domain/service/AuthService.kt` and `domain/service/JwtService.kt`. Routes only catch exceptions and map to HTTP codes — no business logic in routes.

---

## Endpoints & Flows

### POST /auth/register
1. Validate `RegisterRequest` (email, username, password rules below)
2. Check email uniqueness → `UserAlreadyExistsException` (409) if taken
3. Hash password with BCrypt cost 12
4. Persist user via `UserRepository`
5. Generate JWT + refresh token pair
6. Return `AuthResponse { accessToken, refreshToken, user }`

### POST /auth/login
1. Validate `LoginRequest`
2. Lookup user by email — if not found: call `simulatePasswordCheck()` for constant-time response, then throw `InvalidCredentialsException` (never 404)
3. BCrypt verify — mismatch → `InvalidCredentialsException` (401)
4. Generate JWT + refresh token
5. Return `AuthResponse`

### POST /auth/refresh
1. Hash incoming token with SHA-256
2. Lookup by `token_hash` in `refresh_tokens`
3. If revoked: revoke **all** user tokens → `TokenReuseDetectedException` (401)
4. If expired: `InvalidRefreshTokenException` (401)
5. Issue new JWT + new refresh token; revoke old token (same service call, not DB transaction)
6. Return `AuthResponse`

---

## Token Internals

### JWT
- Algorithm: HS256
- Claims: `userId` (Long), `email` (String), `jti` (UUID), `iat`, `exp`
- Expiry: 1h (set in `application.yaml` or `JWT_SECRET` env var)
- Extract in routes: `call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asLong()`

### Refresh Token
- Generate: 64 random bytes → Base64 → sent to client as opaque string
- Store: SHA-256 hash (Base64) in `refresh_tokens.token_hash` — **not BCrypt** (BCrypt truncates at 72 bytes, SHA-256 is safe for 64-byte random data)
- Index: `token_hash` column is indexed for lookup performance
- Columns: `token_hash`, `user_id`, `expires_at`, `is_revoked`

---

## Validation Rules

| Field | Rule |
|-------|------|
| Email | RFC 5321, max 254 chars, normalized lowercase |
| Username | 3–100 chars, alphanumeric + `.` `-` `_` |
| Password | 8–128 chars, requires ≥1 letter and ≥1 digit |
| All inputs | XSS patterns rejected (script tags, SQL keywords, control chars) |

---

## Exception → HTTP Mapping

| Exception | HTTP | Notes |
|-----------|------|-------|
| `ValidationException` | 400 | Input failed validation |
| `UserAlreadyExistsException` | 409 | Email already registered |
| `InvalidCredentialsException` | 401 | Wrong password or unknown email |
| `InvalidRefreshTokenException` | 401 | Expired or malformed token |
| `TokenReuseDetectedException` | 401 | Reuse → all tokens revoked |
| `UserNotFoundException` | 401 | Return 401, not 404 (don't leak existence) |
| `AuthInternalException` | 500 | Log cause server-side; never expose to caller |

Routes must catch each type explicitly — no generic `catch(e: Exception)` that silently swallows.

---

## Security Rules

- Never expose stack traces or internal messages in responses
- All private endpoints: `authenticate("auth-jwt")` wrapper
- Secrets via env var only — never hardcoded in yaml or code
- Passwords never logged, never returned in any response
- `simulatePasswordCheck()` ensures consistent response time on unknown email (timing attack prevention)

---

## Key Files

| Path | Purpose |
|------|---------|
| `domain/service/AuthService.kt` | Registration, login, token refresh |
| `domain/service/JwtService.kt` | JWT generation and validation |
| `infrastructure/auth/` | Ktor JWT plugin config |
| `infrastructure/database/tables/RefreshTokenTable.kt` | Token storage schema |
| `presentation/routes/AuthRoutes.kt` | Route handlers (catch + map only) |
| `domain/exception/AuthException.kt` | Sealed exception hierarchy |
| `application/dto/AuthDto.kt` | `RegisterRequest`, `LoginRequest`, `AuthResponse` |
