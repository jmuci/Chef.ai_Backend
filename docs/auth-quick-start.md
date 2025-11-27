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
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "Alice",
  "email": "alice@example.com"
}
```

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
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "Alice",
  "email": "alice@example.com"
}
```

### Step 3: Access Protected Endpoints

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

## Token Expiration

Tokens expire after 1 hour by default. When a token expires, you'll receive a 401 Unauthorized response.
Simply login again to get a new token.

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

## Next Steps

- Explore the full API documentation
- Implement recipe collections
- Add OAuth integration (coming soon)
- Implement recipe sharing features

## Support

For issues or questions:

1. Check the `AUTH_IMPLEMENTATION_SUMMARY.md` for detailed documentation
2. Review error messages and troubleshooting guide above
3. Check application logs for detailed error information
