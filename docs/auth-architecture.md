# ChefAI Authentication & Authorization Architecture

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Authentication Flow](#authentication-flow)
3. [Authorization Model](#authorization-model)
4. [Component Diagram](#component-diagram)
5. [Security Considerations](#security-considerations)
6. [Future Enhancements](#future-enhancements)

## Architecture Overview

The ChefAI application implements a layered JWT-based authentication and authorization system following best practices
for RESTful APIs. The architecture consists of three main layers:

### 1. Presentation Layer

- **Authentication Routes** (`AuthRoutes.kt`): Handles user registration and login
- **Protected Routes** (`Routing.kt`): All `/recipes` endpoints require valid JWT
- **JWT Principal Extraction**: Middleware extracts user context from JWT tokens

### 2. Domain Layer

- **AuthService**: Business logic for user authentication
- **JwtService**: JWT token generation and verification
- **RecipesService**: Business logic with authorization checks

### 3. Infrastructure Layer

- **UserRepository**: Data access for user management
- **RecipesRepository**: Data access with user ownership
- **JWT Configuration**: Ktor plugin configuration for JWT auth
- **Database Schema**: PostgreSQL with user and recipe relationships

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
┌────────────────────────▼────────────────────────────────────────┐
│                   POSTGRESQL DATABASE                            │
│                                                                  │
│  ┌────────────────┐              ┌──────────────────────────┐  │
│  │  users table   │              │    recipe table          │  │
│  │                │              │                          │  │
│  │  • id (PK)     │              │  • uuid (PK)             │  │
│  │  • email       │◄─────────────┤  • user_id (FK)          │  │
│  │  • username    │   1:Many     │  • title                 │  │
│  │  • password    │              │  • description           │  │
│  │  • created_at  │              │  • is_public             │  │
│  └────────────────┘              │  • ...                   │  │
│                                   └──────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Security Considerations

### Password Security

- **Hashing Algorithm**: BCrypt with cost factor 12
- **Salt**: Automatically generated per password
- **Plain text**: Never stored or logged
- **Validation**: Minimum 8 chars, must contain letters and numbers

### Token Security

- **Algorithm**: HMAC-SHA256 (HS256)
- **Expiration**: 1 hour (configurable)
- **Claims**: Minimal (userId, email only)
- **Signature**: Verified on every request
- **Secret**: Configurable via environment variable

### Transport Security

- **HTTPS**: Required in production
- **Headers**: Authorization Bearer token format
- **CORS**: Should be configured for production

### Database Security

- **Password Column**: Never exposed in API responses
- **Prepared Statements**: Exposed ORM prevents SQL injection
- **Indexes**: Email indexed for performance
- **Unique Constraints**: Email uniqueness enforced at DB level

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

### Phase 2: Refresh Tokens

```kotlin
data class TokenPair(
    val accessToken: String,      // Short-lived (15 min)
    val refreshToken: String,      // Long-lived (7 days)
    val expiresIn: Int
)

// New endpoint
post("/auth/refresh") {
    val refreshToken = call.receive<RefreshTokenRequest>()
    val newTokenPair = authService.refreshTokens(refreshToken)
    call.respond(newTokenPair)
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

-- For future features
CREATE INDEX idx_recipe_created_at ON recipe(created_at);
CREATE INDEX idx_recipe_label ON recipe(label);
```

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
