#!/usr/bin/env bash
set -eu

COMPOSE_FILE="docker-compose.smoketest.yaml"

# 1. Start from clean state
docker compose -f $COMPOSE_FILE down -v || true
docker compose -f $COMPOSE_FILE up -d --build

echo "Waiting for db to be ready..."
for i in {1..30}; do
  if docker exec smoketest_postgres_db pg_isready -U postgres; then
    echo "Postgres ready."
    break
  fi
  sleep 2
done

echo "Waiting for service health"
for i in {1..30}; do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/health || true)
  if [ "$code" == "200" ]; then
    echo "Service is healthy."
    break
  fi
  sleep 3
done

# 2. Register a new user
echo "Registering user..."
REGISTER_RESPONSE=$(mktemp)
REGISTER_HTTP_CODE=$(
  curl -s -o "$REGISTER_RESPONSE" -w "%{http_code}" --fail \
    -X POST http://localhost:8082/auth/register \
    -H "Content-Type: application/json" \
    -d '{"email":"smoketest@example.com","username":"Smokey","password":"TestPass123"}'
) || {
    echo "Curl failed with HTTP code: $REGISTER_HTTP_CODE"
    cat "$REGISTER_RESPONSE"
    exit 1
}

if [[ "$REGISTER_HTTP_CODE" != "201" ]]; then
  echo "User registration failed (HTTP $REGISTER_HTTP_CODE):"
  cat "$REGISTER_RESPONSE"
  exit 1
fi

TOKEN=$(jq -e -r .token <"$REGISTER_RESPONSE") || {
  echo "No token found in register response:"
  cat "$REGISTER_RESPONSE"
  exit 1
}

# 3. Login with the new user
echo "Logging in..."
LOGIN=$(curl -s -X POST http://localhost:8082/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"smoketest@example.com","password":"TestPass123"}')
echo "$LOGIN"
LOGIN_TOKEN=$(echo "$LOGIN" | jq -r .token)
if [ -z "$LOGIN_TOKEN" ] || [ "$LOGIN_TOKEN" == "null" ]; then
  echo "Login failed"
  exit 1
fi

# 4. Create a recipe
RECIPE_RESPONSE=$(mktemp)
RECIPE_HTTP_CODE=$(curl -s -o "$RECIPE_RESPONSE" -w "%{http_code}" --fail \
  -X POST http://localhost:8082/recipes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $LOGIN_TOKEN" \
  -d '{
        "title": "Test Recipe",
        "description": "A quick smoketest recipe.",
        "imageUrl": "http://example.com/img.png",
        "imageUrlThumbnail": "http://example.com/thumb.png",
        "prepTimeMinutes": 5,
        "cookTimeMinutes": 5,
        "servings": 1,
        "privacy": "PUBLIC",
        "recipeExternalUrl": null
      }' \
) || {
    echo "Recipe creation curl failed with HTTP code: $RECIPE_HTTP_CODE"
    cat "$RECIPE_RESPONSE"
    exit 1
}

if [[ "$RECIPE_HTTP_CODE" != "201" ]]; then
  echo "Recipe creation failed (HTTP $RECIPE_HTTP_CODE):"
  cat "$RECIPE_RESPONSE"
  exit 1
fi

echo "Created recipe response:"
cat "$RECIPE_RESPONSE"
echo

# 5. Get recipes as an authenticated user
echo "Getting recipes..."
RECIPES=$(curl -s -X GET http://localhost:8082/recipes \
  -H "Authorization: Bearer $LOGIN_TOKEN")

RECIPE_COUNT=$(echo "$RECIPES" | jq 'length')

if [ "$RECIPE_COUNT" -ge 1 ]; then
  echo "Successfully retrieved $RECIPE_COUNT recipes!"
else
  echo "Failed to retrieve recipes or none found."
  exit 1
fi

echo "Smoke test PASSED ✅"

docker compose -f $COMPOSE_FILE down -v
