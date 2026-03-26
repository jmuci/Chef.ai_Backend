# Recipe Extraction

## Overview

Deterministic recipe extraction from web URLs using structured data parsing. No LLM or heuristic scraping involved — extraction relies exclusively on schema.org markup embedded in pages.

## Endpoint

```
POST /api/recipes/extract
Authorization: Bearer <JWT>
Content-Type: application/json
```

### Request

```json
{
  "url": "https://www.allrecipes.com/recipe/12345/chocolate-cake/"
}
```

### Response (200 OK)

```json
{
  "title": "Chocolate Cake",
  "description": "A rich, moist chocolate cake",
  "servings": "12 servings",
  "prepTimeMinutes": 30,
  "cookTimeMinutes": 45,
  "totalTimeMinutes": 75,
  "ingredients": [
    {
      "raw": "2 cups all-purpose flour, sifted",
      "quantity": "2",
      "unit": "cup",
      "name": "all-purpose flour",
      "preparation": "sifted"
    },
    {
      "raw": "1½ cups sugar",
      "quantity": "1 1/2",
      "unit": "cup",
      "name": "sugar",
      "preparation": null
    }
  ],
  "steps": [
    { "order": 1, "text": "Preheat oven to 350°F" },
    { "order": 2, "text": "Mix dry ingredients in a bowl" }
  ],
  "tags": ["dessert", "chocolate", "baking"],
  "yieldAmount": "12 servings",
  "sourceUrl": "https://www.allrecipes.com/recipe/12345/chocolate-cake/"
}
```

## Extraction Strategy Chain

Extractors run in priority order. The first one to return a result wins:

| Priority | Extractor | Source | Coverage |
|----------|-----------|--------|----------|
| 1 | **JSON-LD** | `<script type="application/ld+json">` with `@type: "Recipe"` | Full recipe (title, times, ingredients, steps, tags) |
| 2 | **Microdata** | `itemtype="schema.org/Recipe"` + `itemprop` attributes | Full recipe |
| 3 | **Meta Tags** | `og:title`, `twitter:title`, `<meta name="description">` | Title + description only |

If all three return null, the endpoint responds with **422 Unprocessable Entity**.

### JSON-LD Extractor

Handles the most common structured data format. Parses all `<script type="application/ld+json">` blocks and searches for a node with `@type: "Recipe"`.

**Supported structures:**
- Single Recipe object at top level
- `@graph` arrays containing a Recipe node
- Nested arrays of JSON-LD objects
- Array `@type` values (e.g., `["Recipe", "Thing"]`)

**Field mapping:**

| JSON-LD field | Response field | Notes |
|---------------|---------------|-------|
| `name` / `headline` | `title` | Falls back to `headline` if `name` missing |
| `description` | `description` | HTML stripped if present |
| `recipeYield` | `servings`, `yieldAmount` | Handles string or array (uses first element) |
| `prepTime` | `prepTimeMinutes` | ISO 8601 duration → minutes |
| `cookTime` | `cookTimeMinutes` | ISO 8601 duration → minutes |
| `totalTime` | `totalTimeMinutes` | ISO 8601 duration → minutes |
| `recipeIngredient` | `ingredients` | Array of strings → parsed into structured ingredients |
| `recipeInstructions` | `steps` | String array, `HowToStep` array, or `HowToSection` with nested steps |
| `keywords` | `tags` | Comma-separated string or array |
| `recipeCategory` | `tags` | Merged with keywords |
| `recipeCuisine` | `tags` | Merged with keywords |

### Microdata Extractor

Parses HTML5 microdata attributes (`itemtype`, `itemprop`).

**Time field resolution** (in priority order):
1. `datetime` attribute (on `<time>` elements)
2. `content` attribute (on `<meta>` elements)
3. Text content

**Ingredient aliases:** Supports both `itemprop="recipeIngredient"` and legacy `itemprop="ingredients"`.

### Meta Tag Extractor

Last-resort fallback. Only extracts title and description from structured meta tags (Open Graph or Twitter):

| Priority | Title source | Description source |
|----------|-------------|-------------------|
| 1 | `og:title` | `og:description` |
| 2 | `twitter:title` | `twitter:description` |
| 3 (for desc only) | — | `<meta name="description">` |

**Important:** Requires at least an `og:title` or `twitter:title` to return a result. Does NOT fall back to generic `<title>` tags, which prevents false positives (e.g., extracting "Google" from google.com).

All other fields (ingredients, steps, times, tags) are null/empty. Returns `null` if no structured title is found.

## Ingredient Parsing

Raw ingredient strings are parsed into structured components:

```
"1½ cups all-purpose flour, sifted"
  → quantity: "1 1/2"
  → unit: "cup"
  → name: "all-purpose flour"
  → preparation: "sifted"
```

### Supported formats

| Format | Example | Parsed quantity |
|--------|---------|----------------|
| Integer | `2 cups flour` | `"2"` |
| Decimal | `1.5 cups flour` | `"1.5"` |
| Fraction | `1/2 cup flour` | `"1/2"` |
| Mixed number | `1 1/2 cups flour` | `"1 1/2"` |
| Unicode fraction | `1½ cups flour` | `"1 1/2"` |
| Range | `1-2 tablespoons salt` | `"1-2"` |
| Parenthetical | `2 (14.5 oz) cans tomatoes` | `"2 (14.5 oz)"` |

### Unit normalization

Common abbreviations are normalized to canonical forms:

| Abbreviation | Canonical |
|---|---|
| `tbsp`, `tbs`, `T` | `tablespoon` |
| `tsp`, `t` | `teaspoon` |
| `oz` | `ounce` |
| `lb`, `lbs` | `pound` |
| `c` | `cup` |
| `g` | `gram` |
| `ml` | `milliliter` |
| `l` | `liter` |
| `kg` | `kilogram` |

*(60+ unit aliases supported — see `IngredientParser.kt` for the full map)*

### Preparation extraction

- **Comma-separated:** `"cheese, shredded"` → prep: `"shredded"`
- **Parenthetical:** `"chicken (boneless, skinless)"` → prep: `"boneless, skinless"`

## ISO 8601 Duration Parsing

Time fields in JSON-LD/microdata use ISO 8601 durations:

| Duration | Minutes |
|----------|---------|
| `PT30M` | 30 |
| `PT1H` | 60 |
| `PT1H30M` | 90 |
| `P1DT2H30M` | 1590 |
| `PT30S` | 1 (rounded up) |
| `30` (plain number) | 30 (fallback) |

## Error Handling

| HTTP Status | Exception | When |
|---|---|---|
| **400** Bad Request | `InvalidUrlException` | URL is blank, malformed, non-HTTP, or missing host |
| **400** Bad Request | `SerializationException` | Request body is not valid JSON |
| **422** Unprocessable Entity | `NoRecipeDataException` | Page fetched successfully but no structured recipe data found |
| **502** Bad Gateway | `FetchFailedException` | Target URL returned HTTP error, timed out, or is unreachable |
| **401** Unauthorized | (Ktor auth) | Missing or invalid JWT token |
| **500** Internal Server Error | `Exception` | Unexpected server error |

Error response format:
```json
{
  "message": "No structured recipe data found at the provided URL"
}
```

## HTTP Fetch Behavior

When fetching the target URL:

- **User-Agent:** Recent Chrome UA string (avoids bot blocking)
- **Timeout:** 10 seconds
- **Max body size:** 5 MB
- **Redirects:** Followed automatically
- **Compression:** Accepts gzip/deflate

## Architecture

```
ExtractionRoutes (presentation)
  → RecipeExtractionService (domain/service) — URL validation, orchestration
    → RecipeExtractor interface (domain/extraction)
      → JsoupRecipeExtractor (infrastructure) — strategy chain
        → HtmlFetcher — Jsoup HTTP fetch
        → JsonLdExtractor — JSON-LD parsing
        → MicrodataExtractor — Microdata parsing
        → MetaTagExtractor — Meta tag fallback

IngredientParser (domain/extraction) — raw string → structured ingredient
IsoDurationParser (domain/extraction) — ISO 8601 → minutes
```

## Smoke Test

```bash
# 1. Get auth token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test1@ex.com","password":"test123!"}' | jq -r '.token')

# 2. Extract a recipe
curl -s -X POST http://localhost:8080/api/recipes/extract \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.allrecipes.com/recipe/10813/best-chocolate-chip-cookies/"}' | jq .

# 3. Test error cases
# Missing URL → 400
curl -s -X POST http://localhost:8080/api/recipes/extract \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"url":""}' | jq .

# Non-recipe page → 422
curl -s -X POST http://localhost:8080/api/recipes/extract \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}' | jq .
```

## Limitations

- **No LLM fallback:** If a page lacks JSON-LD, microdata, and meta tags, extraction fails with 422.
- **No heuristic scraping:** Does not attempt to find ingredients/steps by HTML structure or position.
- **Meta tags only:** The meta tag extractor provides title + description only — no ingredients, steps, or times.
- **JavaScript-rendered content:** Pages that require JS execution to render recipe data will not be extracted (Jsoup does not execute JavaScript).
- **Rate limiting:** No built-in rate limiting on the extraction endpoint. The 10s fetch timeout provides basic protection against slow origins.
