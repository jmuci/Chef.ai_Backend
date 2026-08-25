#!/usr/bin/env bash
set -eu

COMPOSE_FILE="docker-compose.smoketest.yaml"

new_uuid() {
  uuidgen | tr '[:upper:]' '[:lower:]'
}

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
LOGIN_USER_ID=$(echo "$LOGIN" | jq -r .userId)
if [ -z "$LOGIN_TOKEN" ] || [ "$LOGIN_TOKEN" == "null" ]; then
  echo "Login failed"
  exit 1
fi
if [ -z "$LOGIN_USER_ID" ] || [ "$LOGIN_USER_ID" == "null" ]; then
  echo "Login response missing userId"
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

RECIPE_UUID=$(jq -e -r .uuid <"$RECIPE_RESPONSE") || {
  echo "No uuid found in recipe creation response:"
  cat "$RECIPE_RESPONSE"
  exit 1
}

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

# 6. Push one sync recipe aggregate
echo "Pushing sync recipe..."
SYNC_RECIPE_UUID=$(new_uuid)
SYNC_STEP_UUID=$(new_uuid)
NOW_MS=$(( $(date +%s) * 1000 ))

SYNC_PUSH_PAYLOAD=$(
  jq -n \
    --arg recipeUuid "$SYNC_RECIPE_UUID" \
    --arg stepUuid "$SYNC_STEP_UUID" \
    --arg creatorId "$LOGIN_USER_ID" \
    --argjson updatedAt "$NOW_MS" \
    '{
      recipes: [
        {
          uuid: $recipeUuid,
          title: "Sync Smoke Recipe",
          description: "Recipe created from smoke test",
          imageUrl: "https://example.com/image.jpg",
          imageUrlThumbnail: "https://example.com/thumb.jpg",
          prepTimeMinutes: 10,
          cookTimeMinutes: 20,
          servings: 2,
          creatorId: $creatorId,
          recipeExternalUrl: null,
          privacy: "PRIVATE",
          updatedAt: $updatedAt,
          deletedAt: null,
          steps: [
            {
              uuid: $stepUuid,
              orderIndex: 0,
              instruction: "Boil water"
            }
          ],
          ingredients: [],
          tagIds: [],
          labelIds: []
        }
      ]
    }'
)

SYNC_PUSH_RESPONSE=$(mktemp)
SYNC_PUSH_HTTP_CODE=$(curl -s -o "$SYNC_PUSH_RESPONSE" -w "%{http_code}" \
  -X POST http://localhost:8082/sync/push \
  -H "Authorization: Bearer $LOGIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$SYNC_PUSH_PAYLOAD" \
) || {
    echo "Sync push curl failed with HTTP code: $SYNC_PUSH_HTTP_CODE"
    cat "$SYNC_PUSH_RESPONSE"
    exit 1
}

if [[ "$SYNC_PUSH_HTTP_CODE" != "200" ]]; then
  echo "Sync push failed (HTTP $SYNC_PUSH_HTTP_CODE):"
  cat "$SYNC_PUSH_RESPONSE"
  exit 1
fi

if ! jq -e '
  (.accepted | type == "array") and
  (.conflicts | type == "array") and
  (.errors | type == "array")
' <"$SYNC_PUSH_RESPONSE" >/dev/null; then
  echo "Sync push response missing required arrays:"
  cat "$SYNC_PUSH_RESPONSE"
  exit 1
fi

PUSH_ACCEPTED_COUNT=$(jq '.accepted | length' <"$SYNC_PUSH_RESPONSE")
if [ "$PUSH_ACCEPTED_COUNT" -lt 1 ]; then
  echo "Sync push did not accept any entities:"
  cat "$SYNC_PUSH_RESPONSE"
  exit 1
fi

# 7. Pull sync deltas and validate shape/cursor behavior fields
echo "Pulling sync deltas..."
SYNC_PULL_RESPONSE=$(mktemp)
SYNC_PULL_HTTP_CODE=$(curl -s -o "$SYNC_PULL_RESPONSE" -w "%{http_code}" \
  "http://localhost:8082/sync/pull?since=0&limit=100" \
  -H "Authorization: Bearer $LOGIN_TOKEN" \
) || {
    echo "Sync pull curl failed with HTTP code: $SYNC_PULL_HTTP_CODE"
    cat "$SYNC_PULL_RESPONSE"
    exit 1
}

if [[ "$SYNC_PULL_HTTP_CODE" != "200" ]]; then
  echo "Sync pull failed (HTTP $SYNC_PULL_HTTP_CODE):"
  cat "$SYNC_PULL_RESPONSE"
  exit 1
fi

if ! jq -e '
  (.recipes | type == "array") and
  (.serverTimestamp | type == "number") and
  (.hasMore | type == "boolean")
' <"$SYNC_PULL_RESPONSE" >/dev/null; then
  echo "Sync pull response missing required fields:"
  cat "$SYNC_PULL_RESPONSE"
  exit 1
fi

if ! jq -e --arg recipeUuid "$SYNC_RECIPE_UUID" '.recipes | any(.uuid == $recipeUuid)' <"$SYNC_PULL_RESPONSE" >/dev/null; then
  echo "Sync pull did not include pushed recipe UUID $SYNC_RECIPE_UUID:"
  cat "$SYNC_PULL_RESPONSE"
  exit 1
fi

# 8. Home layout (anonymous, no Authorization header)
echo "Fetching home layout (anonymous)..."
HOME_LAYOUT_RESPONSE=$(mktemp)
HOME_LAYOUT_HTTP_CODE=$(curl -s -o "$HOME_LAYOUT_RESPONSE" -w "%{http_code}" \
  http://localhost:8082/api/v1/home/layout \
) || {
    echo "Home layout curl failed with HTTP code: $HOME_LAYOUT_HTTP_CODE"
    cat "$HOME_LAYOUT_RESPONSE"
    exit 1
}

if [[ "$HOME_LAYOUT_HTTP_CODE" != "200" ]]; then
  echo "Home layout fetch failed (HTTP $HOME_LAYOUT_HTTP_CODE):"
  cat "$HOME_LAYOUT_RESPONSE"
  exit 1
fi

if ! jq -e '
  (.schemaVersion | type == "string") and
  (.layoutChecksum | type == "string") and
  (.components | type == "array")
' <"$HOME_LAYOUT_RESPONSE" >/dev/null; then
  echo "Home layout response missing required fields:"
  cat "$HOME_LAYOUT_RESPONSE"
  exit 1
fi

# 9. Home layout again, this time with If-None-Match set to the checksum we just got - expect 304
echo "Re-fetching home layout with matching ETag (expect 304)..."
LAYOUT_CHECKSUM=$(jq -e -r .layoutChecksum <"$HOME_LAYOUT_RESPONSE")
HOME_LAYOUT_304_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8082/api/v1/home/layout \
  -H "If-None-Match: \"$LAYOUT_CHECKSUM\"" \
)

if [[ "$HOME_LAYOUT_304_CODE" != "304" ]]; then
  echo "Home layout conditional GET expected 304 but got HTTP $HOME_LAYOUT_304_CODE"
  exit 1
fi

# 10. Recipe search (anonymous) - the recipe created in step 4 is PUBLIC, so it must be
# visible to a caller with no Authorization header.
echo "Searching recipes (anonymous)..."
SEARCH_RESPONSE=$(mktemp)
SEARCH_HTTP_CODE=$(curl -s -o "$SEARCH_RESPONSE" -w "%{http_code}" \
  "http://localhost:8082/api/v1/recipes/search?q=Test" \
) || {
    echo "Recipe search curl failed with HTTP code: $SEARCH_HTTP_CODE"
    cat "$SEARCH_RESPONSE"
    exit 1
}

if [[ "$SEARCH_HTTP_CODE" != "200" ]]; then
  echo "Recipe search failed (HTTP $SEARCH_HTTP_CODE):"
  cat "$SEARCH_RESPONSE"
  exit 1
fi

if ! jq -e --arg recipeUuid "$RECIPE_UUID" '.results | any(.uuid == $recipeUuid)' <"$SEARCH_RESPONSE" >/dev/null; then
  echo "Anonymous search for 'Test' did not include public recipe $RECIPE_UUID:"
  cat "$SEARCH_RESPONSE"
  exit 1
fi

# 11. Recipe search with a missing q param - expect 400
SEARCH_MISSING_Q_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8082/api/v1/recipes/search" \
)
if [[ "$SEARCH_MISSING_Q_CODE" != "400" ]]; then
  echo "Recipe search without q expected 400 but got HTTP $SEARCH_MISSING_Q_CODE"
  exit 1
fi

# 12. Single-recipe detail fetch (anonymous) - the recipe created in step 4 is PUBLIC.
echo "Fetching recipe detail (anonymous)..."
DETAIL_RESPONSE=$(mktemp)
DETAIL_HTTP_CODE=$(curl -s -o "$DETAIL_RESPONSE" -w "%{http_code}" \
  "http://localhost:8082/api/v1/recipes/$RECIPE_UUID" \
) || {
    echo "Recipe detail curl failed with HTTP code: $DETAIL_HTTP_CODE"
    cat "$DETAIL_RESPONSE"
    exit 1
}

if [[ "$DETAIL_HTTP_CODE" != "200" ]]; then
  echo "Recipe detail fetch failed (HTTP $DETAIL_HTTP_CODE):"
  cat "$DETAIL_RESPONSE"
  exit 1
fi

if ! jq -e --arg recipeUuid "$RECIPE_UUID" '.recipe.uuid == $recipeUuid' <"$DETAIL_RESPONSE" >/dev/null; then
  echo "Recipe detail response did not match requested uuid $RECIPE_UUID:"
  cat "$DETAIL_RESPONSE"
  exit 1
fi

# 13. Single-recipe detail fetch for a recipe that doesn't exist - expect 404
NONEXISTENT_UUID=$(new_uuid)
DETAIL_404_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8082/api/v1/recipes/$NONEXISTENT_UUID" \
)
if [[ "$DETAIL_404_CODE" != "404" ]]; then
  echo "Recipe detail for nonexistent uuid expected 404 but got HTTP $DETAIL_404_CODE"
  exit 1
fi

docker compose -f $COMPOSE_FILE down -v

GREEN='\033[0;32m'
RESET='\033[0m'
printf "\n${GREEN}✔ ✅ ✔ Smoke test PASSED ✔ ✅ ✔${RESET}\n"
