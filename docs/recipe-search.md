# Recipe Search & the Browse Taxonomy

`GET /api/v1/recipes/search` backs both the Search tab's typed search and its
browse category cards. This document covers how a query is matched, and the
editorial taxonomy that makes the browse cards return anything at all.

---

## The endpoint

```
GET /api/v1/recipes/search?q={text}&limit={n}&offset={n}
```

| Param | Default | Notes |
|---|---|---|
| `q` | required | 400 if blank. Sanitised by `RecipeSearchQueryBuilder`. |
| `limit` | 20 | Clamped to 1..50 by `RecipeSearchService`. |
| `offset` | 0 | Clamped to 0..500. |

Mounted under `authenticate("auth-jwt", optional = true)`. An **anonymous**
caller is not an error — results are scoped to `PUBLIC` recipes only
(`SEARCH_SQL_PUBLIC_ONLY`); an authenticated one also sees their own recipes
(`SEARCH_SQL_AUTHENTICATED`).

### How a query is matched

`RecipeSearchQueryBuilder.build()` lowercases the input, splits on anything
that is not a letter or digit, and emits each token as a **prefix term ANDed
with the rest**:

```
"kid friendly"  ->  kid:* & friendly:*
"lunch"         ->  lunch:*
```

Those tokens are matched against, in ranking order:

1. **Tag or label display name** — `tag_or_label_hit DESC` sorts these first.
2. **`recipes.search_vector`** — a generated `tsvector` over title (weight A)
   and description (weight C), then `ts_rank DESC`.
3. `uuid ASC` as a stable tiebreak for deterministic paging.

**Two consequences that bite in practice:**

- Tokens are **ANDed**, so a multi-word term needs *every* token present in one
  field. A tag named `LowCarbs` is a single lexeme and is therefore unreachable
  from `low carb` (`carb:*` matches nothing) — the vocabulary word has to be
  spelled `Low Carb` for the browse card to work.
- Recipe **steps are not indexed**. `search_vector` covers title and
  description only, so a word that appears solely in instruction text cannot be
  found.

---

## The browse taxonomy

The client's `SearchCategory` enum sends a literal English word per card. Those
words are ordinary searches, so a card is only non-empty if some tag, label,
title or description matches. TheMealDB's own vocabulary is cuisine- and
ingredient-shaped (`Beef`, `Chinese`, `Starter`, `DinnerParty`,
`HangoverFood`) and contains no notion of *when* a dish is eaten — which is why
tapping **Lunch** returned "No recipes found" (jmuci/ChefAI#182).

`MealTypeClassifier` closes that gap at import time, deriving tags and two
labels from each recipe's category, tags, title and ingredient list.

> **These are synthesized editorial classifications, not sourced facts** — the
> same standing as `TheMealDbMapper.estimateTimingsAndServings`. A dish
> legitimately holds several at once: a soup is Lunch *and* Dinner *and*
> Comfort Food.

### Tags vs labels — a correctness boundary, not a style choice

|  | Used for | Assigned by the classifier? |
|---|---|---|
| **Tags** | Search and display only | Freely — a loose tag costs at most a generous browse result |
| **Labels** | The dietary filter in `findCandidateRecipeIds`, i.e. **meal-plan generation** | Only `High Protein` and `Low Carb`, and only from the ingredient list |

No label encoding an allergen or an ethical commitment (`Gluten-Free`,
`Vegan`, `Nut-Free`, `Dairy-Free`, `Contains Seafood`) is ever inferred. A
wrong one of those is a health claim, not a bad recommendation.

Two rules keep the nutritional labels honest:

- **Eggs and other incidental proteins only count in a dish with no high-carb
  ingredient.** Counting them unconditionally labelled 614 of 879 public
  recipes `High Protein`, apple pie included.
- **A `Dessert` is never given a nutritional label**, whatever its ingredients.

`Low Carb` is a claim about *absence*, so `CARB_FRAGMENTS` is deliberately
broad: a false negative costs one browse result, a false positive is a wrong
dietary claim someone may plan meals on.

### Coverage

Against the seeded catalog (895 recipes, 879 public):

| Card | Results | Card | Results |
|---|---|---|---|
| Dinner | 421 | Comfort Food | 133 |
| Protein Packed | 404 | Healthy | 118 |
| Lunch | 217 | Vegetarian | 109 |
| Dessert | 216 | Low Carb | 87 |
| Kid-Friendly | 82 | Breakfast | 46 |
| Budget | 42 | Snack | 38 |
| Quick & Easy | 22 | Sandwiches/Wraps | 18 |
| Cookies | 11 | **Air Fryer** | **2** |

**Air Fryer is genuinely thin.** The catalog contains exactly two air-fryer
recipes and nothing in TheMealDB's vocabulary implies the appliance, so the tag
is derived from the title alone. It grows with the catalog; there is no
honest way to inflate it now.

`cookies` is answered by recipe titles rather than a tag — inventing a
`Cookies` tag to satisfy a category card would be the tail wagging the dog.

---

## Keeping it working

Two tests guard this, and both fail loudly rather than silently:

- `SeedCatalogLoaderTest.every browse-category tag in the real seed file is
  resolvable` — asserts `seed.sql` actually defines every vocabulary word the
  importer resolves against. A missing one is silently minted as a *duplicate*
  tag under a fresh UUID.
- `PostgresRecipeSearchRepositoryIntegrationTest.every browse-card term is
  answered by its curated tag or label` — inserts one recipe per browse tag
  with a title that deliberately does **not** echo the term, then searches each
  term through real Postgres. This is what catches the `LowCarbs`-style
  tokenisation trap.

The canonical term list lives in `src/test/kotlin/support/BrowseCategories.kt`
and mirrors `SearchCategory.query` on the client. **Adding a card there without
adding backing vocabulary here produces a card that renders "No recipes
found".**
