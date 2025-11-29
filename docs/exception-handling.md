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

## Future Enhancements

- Custom exception handler plugin for consistent error responses
- Structured error codes for client-side error handling
- Error correlation IDs for debugging
- Internationalized error messages
