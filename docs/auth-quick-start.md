# Authentication Quick Start Guide

This guide will help you get started with the new authentication system in ChefAI.

## Prerequisites

- PostgreSQL database running (via Docker or locally)
- Kotlin/JVM development environment
- HTTP client (curl, Postman, or similar)

## Setup


### 1. Configure JWT Settings

The application comes with default JWT settings in `src/main/resources/application.yaml`:

```yaml
jwt:
  secret: "your-secret-key-change-this-in-production"
  issuer: "http://0.0.0.0:8080"
  audience: "jwt-audience"
  realm: "ChefAI"
```

**⚠️ Important**: Change the `secret` before deploying to production!

For production, use environment variables:

```bash
export JWT_SECRET="your-production-secret-key"
export JWT_ISSUER="https://your-domain.com"
```

### 2. Start the Application

```bash
./gradlew run
```

## Using the Authentication API

### Step 1: Register a New User

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "username": "Alice",
    "password": "SecurePass123"
  }'
```

**Success Response** (201 Created):

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "UbyUevp_JK9b_99xwFtH5RX1igi2APytwe8Kimtewyuda4LUc2sOhcbhLyRiQ4AC1XNMkOaXrAwKplv4a5JXrA",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "Alice",
  "email": "alice@example.com",
  "expiresIn": 3600
}
```

**Important**: Save both tokens!

- `token` (access token): Use for API requests (expires in 1 hour)
- `refreshToken`: Use to get new tokens when access token expires (valid for 30 days)

**Validation Requirements**:

- Email must be valid format
- Password must be at least 8 characters
- Password must contain both letters and numbers
- Email must be unique

### Step 2: Login

If you already have an account, login to get a new token:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "SecurePass123"
  }'
```

**Success Response** (200 OK):

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "UbyUevp_JK9b_99xwFtH5RX1igi2APytwe8Kimtewyuda4LUc2sOhcbhLyRiQ4AC1XNMkOaXrAwKplv4a5JXrA",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "Alice",
  "email": "alice@example.com",
  "expiresIn": 3600
}
```

### Step 3: Refresh Your Tokens (When Access Token Expires)

When your access token expires (after 1 hour), use your refresh token to get new tokens:

```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN_HERE"
  }'
```

**Success Response** (200 OK):

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "NEW_REFRESH_TOKEN_HERE",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "expiresIn": 3600
}
```

**Important Notes**:

- ⚠️ The old refresh token is now **invalid** (token rotation for security)
- ✅ Save the new refresh token for future refreshes
- 🔄 You can refresh as many times as needed within the 30-day window
- 🚫 Attempting to reuse an old refresh token will **revoke all your tokens** (security feature)

### Step 4: Access Protected Endpoints

Save the token from the registration/login response and include it in the `Authorization` header:

```bash
# Get all accessible recipes (your own + public ones)
curl -X GET http://localhost:8080/recipes \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"

# Create a new recipe
curl -X POST http://localhost:8080/recipes \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Spaghetti Carbonara",
    "label": "Italian",
    "description": "Classic Roman pasta dish",
    "prepTimeMins": "25",
    "recipeUrl": "https://example.com/carbonara",
    "imageUrl": "https://example.com/carbonara.jpg",
    "imageUrlThumbnail": "https://example.com/carbonara_thumb.jpg"
  }'

# Delete your own recipe
curl -X DELETE "http://localhost:8080/recipes?uuid=RECIPE_ID" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## Authorization Rules

### What You Can Access

1. **Your Own Recipes**: All recipes you created (both public and private)
2. **Public Recipes**: Any recipe marked as public by other users
3. **Cannot Access**: Private recipes created by other users

### Recipe Ownership

- When you create a recipe, you automatically become the owner
- Only the owner can:
    - Delete the recipe
    - Update the recipe (when update endpoint is implemented)
- Anyone can view public recipes

## Error Responses

### 400 Bad Request

Invalid request format or missing required fields.

```json
{
  "message": "All fields are required"
}
```

### 401 Unauthorized

Missing, invalid, or expired token.

```text
Token is not valid or has expired
```

### 403 Forbidden

Token is valid but user doesn't have permission.

```json
{
  "message": "Access denied"
}
```

### 404 Not Found

Resource doesn't exist or user doesn't have access.

```json
{
  "message": "Recipe not found or not authorized"
}
```

## Testing with Different Users

### Create Multiple Users

```bash
# User 1: Alice
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "alice@example.com", "username": "Alice", "password": "AlicePass123"}'

# User 2: Bob
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "bob@example.com", "username": "Bob", "password": "BobPass123"}'
```

### Test Authorization

```bash
# Alice creates a private recipe
ALICE_TOKEN="<alice_token>"
curl -X POST http://localhost:8080/recipes \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Alices Secret Recipe",
    "label": "Mediterranean",
    "description": "Top secret!",
    "prepTimeMins": "30",
    "recipeUrl": "https://example.com/secret",
    "imageUrl": "https://example.com/secret.jpg",
    "imageUrlThumbnail": "https://example.com/secret_thumb.jpg"
  }'

# Bob tries to access Alice's recipe - should get filtered out
BOB_TOKEN="<bob_token>"
curl -X GET http://localhost:8080/recipes \
  -H "Authorization: Bearer $BOB_TOKEN"
# Bob will only see his own recipes + public recipes, not Alice's private recipe
```

## Token Expiration & Refresh

### Access Token Lifecycle

- **Expiration**: 1 hour
- **When expired**: You'll receive a `401 Unauthorized` response
- **What to do**: Use your refresh token to get new tokens (see Step 3)

### Refresh Token Lifecycle

- **Expiration**: 30 days
- **When used**: Old refresh token is immediately invalidated (rotation)
- **When expired**: You must login again
- **Security**: Attempting to reuse a revoked token revokes ALL your tokens

### Best Practices

1. **Store securely**: Keep both tokens in secure storage (not localStorage for web apps)
2. **Automatic refresh**: Implement automatic token refresh before expiration
3. **Handle 401s**: Catch 401 errors, attempt refresh, then retry original request
4. **Logout**: When logging out, discard both tokens (optionally call logout endpoint)

### Example: Automatic Token Refresh

```bash
# Pseudocode for client application
function makeAuthenticatedRequest(endpoint) {
  try {
    response = apiCall(endpoint, accessToken)
    return response
  } catch (401 Unauthorized) {
    # Access token expired, try to refresh
    newTokens = apiCall("/auth/refresh", {refreshToken})
    
    if (newTokens) {
      # Update stored tokens
      accessToken = newTokens.accessToken
      refreshToken = newTokens.refreshToken
      
      # Retry original request
      return apiCall(endpoint, accessToken)
    } else {
      # Refresh token also expired, need to login
      redirectToLogin()
    }
  }
}
```

## Development Tips

### View Token Contents

You can decode JWT tokens (without verifying) at [jwt.io](https://jwt.io):

1. Copy your token
2. Paste it in the "Encoded" section
3. View the payload to see `userId`, `email`, and expiration

### Troubleshooting

**"Token is not valid or has expired"**

- Token format: Must be `Bearer <token>` with a space
- Check token hasn't expired (24h default)
- Verify JWT secret in config matches

**"User not authenticated"**

- Missing Authorization header
- Check token spelling and format
- Ensure no extra spaces or characters

**Registration fails with "email already in use"**

- Each email can only be used once
- Use a different email or login instead

**Password validation fails**

- Minimum 8 characters
- Must contain at least one letter
- Must contain at least one number
- Example valid passwords: "Password1", "MyPass123", "SecureP4ss"

## Security Features

### Token Rotation

Every time you refresh your tokens:

1. ✅ New access token issued
2. ✅ New refresh token issued
3. ❌ Old refresh token immediately revoked

This prevents token theft exploitation - even if someone steals your refresh token, they can only use it once.

### Reuse Detection

If someone tries to reuse an old refresh token (security breach detected):

1. 🚨 All refresh tokens for that user are revoked
2. ❌ User must login again on all devices
3. 📝 Security event logged

This protects you if:

- Your refresh token is stolen and used
- An attacker tries to maintain access after you refresh

### Input Validation

All inputs are validated and sanitized:

- ✅ Email format validated (RFC 5321)
- ✅ Email normalized to lowercase
- ✅ Username restricted to safe characters
- ✅ XSS attack patterns rejected
- ✅ SQL injection prevented (parameterized queries)
- ✅ Control characters rejected

### Password Security

- ✅ BCrypt hashing (cost factor 12)
- ✅ Automatic salt generation
- ✅ Never stored in plaintext
- ✅ Never logged or exposed
- ✅ Timing attack protection (constant-time responses)

### Refresh Token Storage

- ✅ Stored as SHA-256 hash (not plaintext)
- ✅ Unique index prevents duplicates
- ✅ Expiration tracked
- ✅ Revocation status tracked
- ✅ Can revoke individually or all at once

## Next Steps

- Review `docs/auth-architecture.md` for detailed architecture
- Explore the full API documentation
- Implement automatic token refresh in your client
- Add recipe collections and sharing features
- Consider implementing OAuth for social login

## Support

For issues or questions:

1. Check `docs/auth-architecture.md` for detailed documentation
2. Review error messages and troubleshooting guide above
3. Check application logs for detailed error information
4. Run tests: `./gradlew test` to verify your setup
