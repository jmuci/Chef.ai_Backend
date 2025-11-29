# ChefAI Authentication & Authorization Architecture

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Authentication Flow](#authentication-flow)
3. [Authorization Model](#authorization-model)
4. [Component Diagram](#component-diagram)
5. [Security Considerations](#security-considerations)
6. [Future Enhancements](#future-enhancements)

## Architecture Overview

The ChefAI application implements a layered JWT-based authentication and authorization system with **refresh token
rotation** following best practices for RESTful APIs. The architecture consists of three main layers:

### 1. Presentation Layer

- **Authentication Routes** (`AuthRoutes.kt`): Handles user registration, login, and token refresh
- **Protected Routes** (`Routing.kt`): All `/recipes` endpoints require valid JWT
- **JWT Principal Extraction**: Middleware extracts user context from JWT tokens

### 2. Domain Layer

- **AuthService**: Business logic for user authentication and token management
- **JwtService**: JWT token generation, verification, and refresh token creation
- **RecipesService**: Business logic with authorization checks
- **InputValidator**: Input sanitization and validation

### 3. Infrastructure Layer

- **UserRepository**: Data access for user management
- **RefreshTokenRepository**: Secure storage of refresh tokens with revocation
- **RecipesRepository**: Data access with user ownership
- **JWT Configuration**: Ktor plugin configuration for JWT auth
- **Database Schema**: PostgreSQL with user, recipe, and refresh_token relationships

## Authentication Flow

### Registration Flow

```
Client                    Server                     Database
  |                         |                            |
  |---POST /auth/register-->|                            |
  |  {email, username,      |                            |
  |   password}             |                            |
  |                         |                            |
  |                         |--Validate Email Format---->|
  |                         |--Validate Password------->|
  |                         |--Check Email Unique------->|
  |                         |                            |
  |                         |--Hash Password------------>|
  |                         |  (BCrypt, cost=12)         |
  |                         |                            |
  |                         |--Create User--------------->|
  |                         |                            |
  |                         |<--User Created--------------|
  |                         |                            |
  |                         |--Generate JWT Token------->|
  |                         |  {userId, email, exp}      |
  |                         |                            |
  |<--201 {token, userId}---|                            |
  |   username, email}      |                            |
```

### Login Flow

```
Client                    Server                     Database
  |                         |                            |
  |---POST /auth/login----->|                            |
  |  {email, password}      |                            |
  |                         |                            |
  |                         |--Find User By Email-------->|
  |                         |                            |
  |                         |<--User + Password Hash-----|
  |                         |                            |
  |                         |--Verify Password---------->|
  |                         |  (BCrypt.verify)           |
  |                         |                            |
  |                         |--Generate JWT Token------->|
  |                         |  {userId, email, exp}      |
  |                         |                            |
  |<--200 {token, userId}---|                            |
  |   username, email}      |                            |
```

### Protected Endpoint Access

```
Client                    Server                     Database
  |                         |                            |
  |---GET /recipes--------->|                            |
  | Authorization: Bearer   |                            |
  | <JWT_TOKEN>             |                            |
  |                         |                            |
  |                         |--Verify JWT Signature----->|
  |                         |--Check Expiration-------->|
  |                         |--Validate Audience------->|
  |                         |                            |
  |                         |--Extract userId----------->|
  |                         |                            |
  |                         |--Get User Recipes---------->|
  |                         |--Get Public Recipes-------->|
  |                         |                            |
  |                         |<--Accessible Recipes--------|
  |                         |                            |
  |<--200 [recipes]---------|                            |
```

### Token Refresh Flow (with Rotation)

```
Client                    Server                     Database
  |                         |                            |
  |---POST /auth/refresh--->|                            |
  |  {refreshToken}         |                            |
  |                         |                            |
  |                         |--Hash Token (SHA-256)----->|
  |                         |                            |
  |                         |--Lookup Token Hash--------->|
  |                         |<--Token Record (if found)---|
  |                         |                            |
  |                         |--Check isRevoked---------->|
  |                         |--Check Expiration--------->|
  |                         |--Verify User Exists-------->|
  |                         |                            |
  |                         |--Generate NEW Tokens------->|
  |                         |  • New Access Token (JWT)  |
  |                         |  • New Refresh Token       |
  |                         |                            |
  |                         |--Store NEW Refresh Token--->|
  |                         |                            |
  |                         |--Revoke OLD Token---------->|
  |                         |  (Token Rotation)          |
  |                         |                            |
  |<--200 {accessToken,-----|                            |
  |   refreshToken,         |                            |
  |   userId}               |                            |
  |                         |                            |
  | Old token now invalid!  |                            |
```

### Token Reuse Detection (Security Feature)

```
Client                    Server                     Database
  |                         |                            |
  |---POST /auth/refresh--->|                            |
  | {OLD_REVOKED_TOKEN}     |                            |
  |                         |                            |
  |                         |--Hash Token--------------->|
  |                         |--Lookup Token-------------->|
  |                         |<--Token (isRevoked=true)---|
  |                         |                            |
  |                         | ⚠️ SECURITY BREACH DETECTED!|
  |                         |                            |
  |                         |--Revoke ALL User Tokens---->|
  |                         |  (Nuclear option)          |
  |                         |                            |
  |<--401 Unauthorized------|                            |
  |                         |                            |
  | Must login again!       |                            |
```

## Authorization Model

### Resource Access Matrix

| Resource          | Owner | Other Users (Public Recipe) | Other Users (Private Recipe) | Unauthenticated |
|-------------------|-------|----------------------------|------------------------------|-----------------|
| View Recipe       | ✅    | ✅                         | ❌                           | ❌              |
| Create Recipe     | ✅    | N/A                        | N/A                          | ❌              |
| Update Recipe     | ✅    | ❌                         | ❌                           | ❌              |
| Delete Recipe     | ✅    | ❌                         | ❌                           | ❌              |
| List All Recipes  | ✅    | ✅ (filtered)              | ❌                           | ❌              |

### Authorization Logic

#### Recipe Creation

```kotlin
// User automatically becomes owner
suspend fun createRecipe(request: CreateRecipeRequest, userId: String): RecipeResponse? {
    // userId extracted from JWT
    return recipesRepository.addRecipe(request, userId)
}
```

#### Recipe Retrieval

```kotlin
// Returns user's recipes + public recipes
suspend fun getAccessibleRecipes(userId: String): List<Recipe> {
    val userRecipes = recipesRepository.recipesByUserId(userId)
    val publicRecipes = recipesRepository.publicRecipes()
    return (userRecipes + publicRecipes).distinctBy { it.uuid }
}
```

#### Recipe Deletion

```kotlin
// Only owner can delete
suspend fun deleteRecipe(recipeId: String, userId: String): Boolean {
    return recipesRepository.removeRecipe(recipeId, userId)
}
```

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT                                   │
│  (Web/Mobile App with HTTP Client)                              │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ HTTP Requests with JWT in header
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                    KTOR SERVER                                   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │           AUTHENTICATION PLUGIN (JWT)                     │  │
│  │  • Verifies JWT signature                                 │  │
│  │  • Validates expiration, audience, issuer                 │  │
│  │  • Extracts principal (userId, email)                     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                         │                                        │
│  ┌──────────────────────▼────────────────────────────────────┐ │
│  │              ROUTING LAYER                                 │ │
│  │                                                            │ │
│  │  ┌────────────────┐      ┌────────────────────────────┐  │ │
│  │  │ Public Routes  │      │   Protected Routes         │  │ │
│  │  │                │      │   (require JWT)            │  │ │
│  │  │ /auth/register │      │   /recipes/*               │  │ │
│  │  │ /auth/login    │      │                            │  │ │
│  │  └────────────────┘      └────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────── │ │
│                         │                                        │
│  ┌──────────────────────▼────────────────────────────────────┐ │
│  │              DOMAIN SERVICES                               │ │
│  │                                                            │ │
│  │  ┌────────────┐  ┌──────────────┐  ┌─────────────────┐  │ │
│  │  │ AuthService│  │  JwtService  │  │ RecipesService  │  │ │
│  │  │            │  │              │  │                 │  │ │
│  │  │ • register │  │ • generate   │  │ • create        │  │ │
│  │  │ • login    │  │ • verify     │  │ • getByUser     │  │ │
│  │  │ • validate │  │              │  │ • getPublic     │  │ │
│  │  └────────────┘  └──────────────┘  └─────────────────┘  │ │
│  └────────────────────────────────────────────────────────── │ │
│                         │                                        │
│  ┌──────────────────────▼────────────────────────────────────┐ │
│  │            DATA ACCESS LAYER                               │ │
│  │                                                            │ │
│  │  ┌──────────────────┐        ┌──────────────────────────┐│ │
│  │  │ UserRepository   │        │  RecipesRepository       ││ │
│  │  │                  │        │                          ││ │
│  │  │ • createUser     │        │  • addRecipe(userId)     ││ │
│  │  │ • findByEmail    │        │  • recipesByUserId       ││ │
│  │  │ • findById       │        │  • publicRecipes         ││ │
│  │  └──────────────────┘        └──────────────────────────┘│ │
│  └────────────────────────────────────────────────────────── │ │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ SQL Queries (Exposed ORM)
                         │
┌────────────────────────▼──────────────────────────────────────────┐
│                   POSTGRESQL DATABASE                             │
│                                                                   │
│  ┌────────────────┐     ┌─────────────────────┐    ┌─────────────┐│
│  │  users table   │     │ refresh_tokens      │    │ recipe      ││
│  │                │     │                     │    │             ││
│  │  • id (PK)     │     │  • id (PK)          │    │  • uuid     ││
│  │  • email       │◄────┤  • user_id (FK)     │    │  • user     ││
│  │  • username    │ 1:M │  • token_hash*      │    │  • title    ││
│  │  • password    │     │  • expires_at       │    │  • desc     ││
│  │  • created_at  │     │  • is_revoked       │    │  • is_public││
│  └────────────────┘     │  • revoked_at       │    │  • ...      ││
│         │               │  • created_at       │    │             ││
│         │               └─────────────────────┘    │             ││
│         └──────────────────1:Many──────────────────┤             ││
│                                                    └─────────────┘│
│                                                                   │
│  * token_hash: SHA-256 hash of refresh token (indexed, unique)    │
└───────────────────────────────────────────────────────────────────┘
```

## Security Considerations

### Password Security

- **Hashing Algorithm**: BCrypt with cost factor 12
- **Salt**: Automatically generated per password
- **Plain text**: Never stored or logged
- **Validation**: Minimum 8 chars, must contain letters and numbers

### Token Security

#### Access Tokens (JWT)

- **Algorithm**: HMAC-SHA256 (HS256)
- **Expiration**: 1 hour (configurable)
- **Claims**: Minimal (userId, email, jti, iat, exp)
- **Signature**: Verified on every request
- **Secret**: Configurable via environment variable
- **Uniqueness**: Each token has unique JWT ID (jti) and issued-at (iat)

#### Refresh Tokens (Opaque)

- **Format**: 64-byte cryptographically secure random bytes (Base64 encoded)
- **Storage**: SHA-256 hashed in database (never stored in plaintext)
- **Expiration**: 30 days (configurable)
- **Rotation**: Old token invalidated immediately when new token issued
- **Reuse Detection**: Attempting to reuse a revoked token triggers full account logout
- **Database Indexes**: Unique index on token_hash for fast O(1) lookups
- **Revocation**: Can be revoked individually or all at once (logout all devices)

### Transport Security

- **HTTPS**: Required in production
- **Headers**: Authorization Bearer token format
- **CORS**: Should be configured for production

### Database Security

- **Password Column**: Never exposed in API responses (separate `UserWithPassword` type)
- **Refresh Token Hashing**: SHA-256 hashed (not BCrypt due to 72-byte limit on random tokens)
- **Prepared Statements**: Exposed ORM prevents SQL injection
- **Indexes**:
    - `users.email` - Regular index for lookups
    - `refresh_tokens.user_id` - Regular index for user-based queries
    - `refresh_tokens.token_hash` - **Unique** index for token validation
    - `refresh_tokens.expires_at` - Regular index for cleanup operations
- **Unique Constraints**:
    - Email uniqueness enforced at DB level
    - Token hash uniqueness prevents duplicates

### Input Validation & Sanitization

- **Email Validation**: RFC 5321 compliant (max 254 chars), normalized to lowercase
- **Username Validation**: 3-100 chars, alphanumeric + `.`, `-`, `_` only
- **Password Validation**: 8-128 chars, requires letter + digit
- **XSS Prevention**: Dangerous patterns rejected (script tags, SQL keywords)
- **Control Characters**: Rejected in all inputs
- **Reserved Usernames**: admin, root, system, etc. blocked
- **Timing Attack Prevention**: Constant-time responses for failed login/register

### Rate Limiting (Recommended)

Currently not implemented but recommended for:

- `/auth/register`: Prevent mass account creation
- `/auth/login`: Prevent brute force attacks
- All endpoints: Prevent DoS attacks

## Future Enhancements

### Phase 1: OAuth Integration (Priority)

```kotlin
// Google OAuth provider
install(Authentication) {
    oauth("google-oauth") {
        urlProvider = { "http://localhost:8080/callback" }
        providerLookup = {
            OAuthServerSettings.OAuth2ServerSettings(
                name = "google",
                authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                clientId = System.getenv("GOOGLE_CLIENT_ID"),
                clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
                defaultScopes = listOf("profile", "email")
            )
        }
        client = HttpClient(Apache)
    }
}
```

### ~~Phase 2: Refresh Tokens~~ ✅ **IMPLEMENTED**

The application now includes a complete refresh token implementation with:

- **Access Tokens**: Short-lived (1 hour), contains user claims
- **Refresh Tokens**: Long-lived (30 days), opaque cryptographically-secure tokens
- **Token Rotation**: Each refresh invalidates the previous refresh token
- **Reuse Detection**: Automatically revokes all tokens if reuse is detected
- **SHA-256 Hashing**: Refresh tokens stored hashed (not BCrypt due to 72-byte limit)
- **Database Storage**: Refresh tokens tracked with expiration and revocation status

```kotlin
// Endpoint implemented in AuthRoutes.kt
post("/auth/refresh") {
    val refreshRequest = call.receive<RefreshTokenRequest>()
    val refreshResponse = authService.refreshToken(refreshRequest)
    if (refreshResponse != null) {
        call.respond(HttpStatusCode.OK, refreshResponse)
    } else {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or expired refresh token"))
    }
}
```

### Phase 3: Role-Based Access Control (RBAC)

```kotlin
enum class UserRole {
    USER,
    PREMIUM_USER,
    MODERATOR,
    ADMIN
}

// Endpoint protection
authenticate("auth-jwt") {
    authorize(UserRole.ADMIN, UserRole.MODERATOR) {
        delete("/recipes/{id}/force") {
            // Admins can delete any recipe
        }
    }
}
```

### Phase 4: Recipe Collections & Sharing

```kotlin
data class Collection(
    val id: String,
    val name: String,
    val userId: String,
    val isPublic: Boolean,
    val recipes: List<String>  // Recipe IDs
)

data class SharedRecipe(
    val recipeId: String,
    val ownerId: String,
    val sharedWithUserId: String,
    val permissions: Set<Permission>  // READ, WRITE
)
```

### Phase 5: Advanced Features

- **Email Verification**: Confirm email addresses on registration
- **Password Reset**: Secure password reset via email
- **Two-Factor Authentication**: TOTP-based 2FA
- **Session Management**: View and revoke active sessions
- **Audit Logging**: Track all authentication events
- **Social Features**: Follow users, favorite recipes
- **API Keys**: For third-party integrations

## Performance Considerations

### Database Indexes

```sql
-- Critical for performance
CREATE INDEX idx_recipe_user_id ON recipe(user_id);
CREATE INDEX idx_recipe_is_public ON recipe(is_public);
CREATE INDEX idx_users_email ON users(email);

-- Refresh token indexes (CRITICAL for security & performance)
CREATE UNIQUE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- For future features
CREATE INDEX idx_recipe_created_at ON recipe(created_at);
CREATE INDEX idx_recipe_label ON recipe(label);
```

#### Index Performance Impact

| Operation          | Without Index   | With Index                 | Impact          |
|--------------------|-----------------|----------------------------|-----------------|
| Token Refresh      | O(n) table scan | O(1) hash lookup           | 🚀 1000x faster |
| Revoke User Tokens | O(n) table scan | O(k) where k=user's tokens | 🚀 100x faster  |
| Cleanup Expired    | O(n) table scan | O(m) where m=expired       | 🚀 10x faster   |

### Caching Strategy (Future)

```kotlin
// Redis cache for JWT validation
val jwtCache = RedisCache<String, JWTPrincipal>()

// Cache user lookups
val userCache = RedisCache<String, User>(ttl = 5.minutes)
```

### Connection Pooling

```kotlin
// Already configured via HikariCP in Exposed
Database.connect(
    url = "jdbc:postgresql://db:5432/chefai_db",
    driver = "org.postgresql.Driver",
    user = "postgres",
    password = "password",
    setupConnection = { connection ->
        connection.autoCommit = false
    }
)
```

## Monitoring & Observability

### Recommended Metrics

- Authentication success/failure rate
- Token generation rate
- Average response time per endpoint
- Active user count
- Failed login attempts per IP
- API calls per user

### Recommended Logging

```kotlin
// Log authentication attempts
log.info("Login attempt for email: ${request.email}")
log.info("Successful login for userId: $userId")
log.warn("Failed login attempt for email: ${request.email}")

// Log authorization failures
log.warn("Unauthorized access attempt to recipe $recipeId by user $userId")

// Never log
// ❌ log.info("Password: ${request.password}")  // NEVER!
// ❌ log.info("Token: $jwtToken")               // NEVER!
```

### Health Checks

```kotlin
get("/health") {
    call.respond(mapOf(
        "status" to "healthy",
        "database" to checkDatabaseConnection(),
        "auth" to "operational"
    ))
}
```

## Testing Strategy

### Unit Tests

- Password hashing and verification
- JWT token generation and validation
- Authorization logic
- Email validation
- Password strength validation

### Integration Tests

- Full registration flow
- Full login flow
- Protected endpoint access
- Token expiration handling
- Invalid token handling

### Security Tests

- SQL injection attempts
- XSS attempts
- Brute force login protection
- Token tampering detection
- Expired token rejection

## Conclusion

This authentication and authorization system provides a solid foundation for securing the ChefAI application. It follows
industry best practices while remaining flexible enough to accommodate future enhancements like OAuth integration and
advanced authorization models.

The layered architecture ensures separation of concerns, making the system maintainable and testable. The use of JWT
tokens provides a stateless authentication mechanism that scales well for distributed systems.

For questions or contributions, please refer to the implementation files and the Quick Start Guide for practical usage
examples.
