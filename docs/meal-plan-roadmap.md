# Meal Plan Generation — Incremental Roadmap

**Status**: Living document — update as phases complete.
**Last updated**: 2026-08-20 (Phase 2.1 done)

This document tracks how meal-plan generation evolves from the current
rule-based pipeline toward something smarter, and sequences the prerequisite
work (recipe catalog growth, small-collection support, preference defaults)
needed to make each step real.

## Where the client-vs-server decision actually lives

There is no ADR in *this* repo for "should meal plan generation run on the
backend or the client" — this repo has no `docs/adr/` folder at all. ADRs
for this product live in the Android client repo,
[`jmuci/ChefAI`](https://github.com/jmuci/ChefAI)`/docs/adrs/`. Two are
adjacent but neither actually covers this decision:

- [`adr-001-hybrid-architecture-choice.md`](https://github.com/jmuci/ChefAI/blob/main/docs/adrs/adr-001-hybrid-architecture-choice.md) —
  general Android architecture style (Modern Android + selective Clean
  Architecture), not about where generation runs.
- [`adr-010-client-side-recipe-scraping.md`](https://github.com/jmuci/ChefAI/blob/main/docs/adrs/adr-010-client-side-recipe-scraping.md) —
  a genuinely relevant precedent, but for a different feature: single-URL
  recipe *import* (user pastes a URL, the app fetches and parses it
  on-device). Its case for client-side was specifically about avoiding
  concentrated-IP bot detection on third-party sites and the lack of a
  shared cache for a one-off, per-user fetch. Neither reason applies to
  meal-plan generation, which reads from **our own** `recipes` table, not a
  third-party site.

The actual generation-location decision was made directly in
`jmuci/ChefAI`'s [`docs/prompts/meal-plans-backend-prompt.md`](https://github.com/jmuci/ChefAI/blob/main/docs/prompts/meal-plans-backend-prompt.md)
(March 2026), which specified a backend async-generation endpoint reusing
the existing sync push/pull protocol — and this repo built exactly that. It
was simply never written up as an ADR. **Recommended follow-up (in the
Android repo, not here): add an ADR recording this decision** — server-side
generation, rationale (centralized algorithm iteration without app
releases, no client-side secret exposure once an LLM is involved,
preferences/history already server-owned via sync), and the explicit
non-goal (a client-side offline fallback using the pre-populated recipe
bundle, per `docs/sync-protocol.md`'s bundling strategy, remains a
legitimate *future* option, not pursued now).

## Phase 0 — Done: deterministic server-side generation

Already shipped (PR #41, merged 2026-04-03) and tested. Despite the
"AI-style" phrase in its doc comment, it's a deterministic shuffle/rotation
algorithm — no LLM involved yet (see Phase 5).

**Backend:**
- `POST /meal-plans/{mealPlanId}/generate` —
  [`presentation/routes/MealPlanRoutes.kt`](../src/main/kotlin/presentation/routes/MealPlanRoutes.kt).
  JWT-authenticated, validates ownership, returns `202 Accepted`.
- Pipeline —
  [`domain/service/MealPlanGenerationService.kt`](../src/main/kotlin/domain/service/MealPlanGenerationService.kt):
  `startGeneration` sets status `GENERATING` and launches a background
  coroutine (`generateAsync`) that queries candidates, assigns them to
  days (`assignRecipesToDays`), replaces `meal_plan_days`, and sets status
  `READY`. On failure, status is left at `GENERATING` so the client can
  detect a stall (no retry logic yet).
- Candidate query —
  [`infrastructure/database/repositoryImpl/PostgresSyncRepository.kt`](../src/main/kotlin/infrastructure/database/repositoryImpl/PostgresSyncRepository.kt)
  `findCandidateRecipeIds`: resolves `recipeSource`
  (`COLLECTION_ONLY` / else own-or-public), intersects with dietary
  restriction tags, applies `maxPrepTimeMinutes`.
- Day assignment: `HIGH` variety never repeats a recipe until every candidate
  has been used once, then round-robins (see Phase 2 — this used to
  partial-fill instead); `MEDIUM` allows a repeat after a 3-day gap; `LOW`
  round-robins freely. `batchCooking` reuses the previous day's recipe on
  odd-indexed days.
- Delivered to the client via the existing sync pull, not a dedicated
  fetch endpoint — status transitions are picked up on next pull.

**Client** (`jmuci/ChefAI`): a 3-step **Create Meal Plan Wizard**
(`WizardBasicsScreen`, `WizardPreferencesScreen`, `WizardAdvancedScreen`,
plus `RecipeSourceSelector`, `DietaryChipGroup`, `MealTypeSelector`,
`ServingsSelector`, `PrepTimeSelector`, `DayLengthSelector`) builds the
`preferencesJson` blob and pushes a `DRAFT` plan via sync, then calls
`/generate`.

**Tests**: `src/test/kotlin/domain/service/MealPlanGenerationServiceTest.kt`,
`src/test/kotlin/infrastructure/auth/MealPlanIntegrationTest.kt`.

## Phase 1 — Recipe catalog growth (~300 recipes, $0, one-off)

**Status: built, one manual step remaining.** The importer, mapper, and two
companion bug fixes below are implemented and tested. What's *not* done is
the actual network fetch — the sandboxed session that built this blocks
outbound HTTPS to arbitrary hosts by policy, so `www.themealdb.com` couldn't
be reached from there. Run the fetch yourself, from any machine/CI with
normal internet access:

```
./gradlew importTheMealDb
psql -h localhost -U postgres -d chefai_db -v ON_ERROR_STOP=1 \
  -f src/main/resources/sql/seed.sql \
  -f src/main/resources/sql/seed_themealdb.sql
```

**`seed.sql` first is mandatory, not stylistic.** Every junction INSERT in the
generated file is one multi-row statement referencing curated catalog UUIDs, so
against a bare schema each aborts wholesale on the first FK violation — recipes
land, tags/labels/ingredients do not, and psql still exits 0 without
`ON_ERROR_STOP`. See jmuci/ChefAI#182, where exactly that produced 789
ingredient-less recipes and an empty "Lunch" browse card. The generated file now
starts with a preflight `DO` block that raises a clear error instead.

The import also attaches the browse taxonomy (`MealTypeClassifier` — meal-type
and style tags plus `High Protein` / `Low Carb`); see
[`docs/recipe-search.md`](recipe-search.md) for the rules and the tag-vs-label
correctness boundary.

**Running it again.** `./gradlew importTheMealDb` re-fetches fresh from
TheMealDB every time and overwrites `seed_themealdb.sql` in place — it's not
additive across runs. Applying the (re)generated file via `psql` is safe to
repeat: every insert is `ON CONFLICT ... DO NOTHING` against deterministic
UUIDs (derived from TheMealDB's `idMeal` for recipes, from normalized names
for new ingredients/tags/labels), so a second apply produces zero duplicate
rows and no errors. The one nuance worth knowing: `DO NOTHING` is not
`DO UPDATE` — if a recipe's content changes upstream on TheMealDB between two
runs, the already-imported row is **not** refreshed; the first import
silently wins because its `INSERT` for that UUID gets skipped on conflict.
Picking up an upstream change requires deleting the existing row(s) first (or
a future enhancement switching that path to `DO UPDATE`).

`importTheMealDb` (new Gradle task, `src/main/kotlin/tools/themealdb/`)
fetches TheMealDB's catalog via a 26-request a–z scan of `search.php?f=`,
maps it onto this schema (`TheMealDbMapper` — deterministic UUIDs, ingredient
catalog dedup against the existing ~302 ingredients, free-text measure
parsing, dietary-category → label mapping), and writes an idempotent
`src/main/resources/sql/seed_themealdb.sql` (every statement is
`ON CONFLICT ... DO NOTHING`, safe to re-run). No DB connection needed for
this step — it only reads `seed.sql` (to dedup against the existing catalog)
and writes a file. Covered by unit tests in
`src/test/kotlin/tools/themealdb/` (measure parsing, instruction splitting,
UUID determinism, catalog reuse, SQL escaping) — no network in tests.

**Two real bugs were found and fixed while building this**, both verified
live against a real Postgres instance + running server, not just unit
tests — without them the import would have been mostly inert:

1. **Recipe privacy casing.** `seed.sql` wrote `'public'`/`'private'`
   (lowercase) for the original 70 recipes and `'PUBLIC'` for the rest, but
   every query that matters (`findCandidateRecipeIds`, `publicRecipes()`,
   `findDeltaRecipes`) filters `privacy = 'PUBLIC'` exactly — and
   `daoToModel`'s `enumValueOf(dao.privacy)` **throws** on lowercase. Fixed
   in `seed.sql` directly; `src/main/resources/sql/fix_privacy_casing.sql`
   is a one-line idempotent migration for any database seeded before this
   fix. Usable public recipes went from 36 → 90 of the 106 seeded.
2. **Dietary restrictions were a silent no-op.** The dietary vocabulary
   (Vegan, Vegetarian, Gluten-Free, ...) lives in `LabelTable`, but
   `findCandidateRecipeIds` resolved `dietaryRestrictionTags` against
   `TagTable` — whose rows are things like `Spicy`/`Breakfast`. No tag ever
   matched, so the filter silently returned every recipe regardless of
   requested diet. Fixed in `PostgresSyncRepository.findCandidateRecipeIds`
   to resolve against `LabelTable` with normalized comparison (uppercase,
   spaces/hyphens → underscore, so `GLUTEN_FREE` matches `Gluten-Free`).
   Regression test:
   `PostgresSyncRepositoryIntegrationTest.findCandidateRecipeIdsFiltersByDietaryLabelNotTag`
   (includes a same-named decoy tag to prove it isn't accidentally matching
   the old way). End-to-end verified: a pushed `DRAFT` plan with
   `dietaryRestrictions: ["VEGAN"]`, generated against the live seed data,
   came back `READY` with every assigned recipe carrying the Vegan label.

Because generation always reads our own `recipes` table (never calls an
external API live), sourcing is a **one-time import job**, not an ongoing
per-request dependency — cost scales with catalog size, not user count.

**Recommended sources, both zero legal/cost risk:**

- **[TheMealDB](https://www.themealdb.com/api.php)** — a free, explicitly
  reusable recipe API (this is licensed reuse, not scraping), ~792 meals
  total. The shared test key (`1`) covers search/filter/lookup endpoints
  for free; only the bulk "list entire database" endpoint requires a
  supporter key. Iterating `search.php?f=<letter>` (a–z) and
  `filter.php?c=<category>` reaches several hundred recipes for $0.
  Requires attribution (e.g. "recipe data via TheMealDB" in an about
  screen or on imported recipes) — trivial to add.
- **[Wikibooks Cookbook](https://en.wikibooks.org/wiki/Cookbook)** — CC
  BY-SA licensed Wikimedia content, explicitly reusable. Good supplementary
  source for cuisine variety once TheMealDB is exhausted.

Together these plausibly reach the ~300 target without touching a single
recipe blog's copyrighted content.

**Explicitly not recommended, despite being technically easy**: scraping
recipe blogs. `adr-010` (in the Android repo) found that several sites
(e.g. `budgetbytes.com`) return clean `200` responses to a plain HTTP
client with no bot-wall — but that repo's own Terms of Service explicitly
prohibits reproduction and derivative works of their content. Scraping
slowly addresses server-load courtesy, not the copyright/ToS problem —
and `adr-010`'s consent context (one ephemeral recipe, fetched once, at a
single user's own initiative) is materially different from bulk-harvesting
hundreds of recipes into a permanent shared database.

**Deferred fallback options**, if TheMealDB + Wikibooks don't provide
enough variety:
- **Spoonacular** free tier — 150 points/day, no card required, ~$0 at this
  project's scale for a slow bulk import. Paid tiers start at $29/mo (Cook)
  if faster/larger import is ever wanted.
- **Edamam** — ruled out: lower tiers restrict permanent caching/storage of
  results, which conflicts with "import and keep."
- Open Kaggle datasets ([Food.com](https://www.kaggle.com/datasets/shuyangli94/food-com-recipes-and-user-interactions),
  [RecipeNLG](https://www.kaggle.com/datasets/paultimothymooney/recipenlg)) —
  large (180K–2M recipes), useful if bulk *text* is wanted for a later
  phase; check each dataset's specific license before use.

**Import mechanics** (for whichever source is used): write into
`recipes` / `recipe_ingredients` / `recipe_steps` / `tags` / `labels` under
a dedicated system "editorial" creator user, `privacy = PUBLIC`; map source
categories onto existing `TagTable`/`LabelTable`/`AllergenTable` rows;
record attribution (e.g. in `recipe_external_url` or a description
footer); rate-limit the importer even against a free/permitted API, as a
courtesy.

If a live-site scraper is ever wanted for a site that explicitly permits
reuse, `adr-010`'s technique (schema.org JSON-LD extraction, tried before a
microdata fallback) is the pattern to reuse — already implemented once as
the `:recipe-scraper` Kotlin Multiplatform module (Ksoup-based) in the
Android repo.

## Phase 2 — Done: fix the "my recipes only" gap

**Status: built.** The client's `RecipeSource.COLLECTION_ONLY` is labeled
**"My collection only."** `findCandidateRecipeIds` used to implement
`COLLECTION_ONLY` as strictly `BookmarkedRecipeTable` rows. Neither the
backend (`RecipesService`) nor the Android client auto-bookmarks a recipe
when its creator authors it — so a user who wrote 5 recipes but never
explicitly bookmarked them got an **empty candidate pool** under "my
collection only," silently contradicting the wizard's own label. This was a
real bug, not a hypothetical, and it directly broke the "5 recipes for 5
days, my own recipes only" scenario this roadmap exists to support.

**Two fixes**, both in
[`domain/service/MealPlanGenerationService.kt`](../src/main/kotlin/domain/service/MealPlanGenerationService.kt)
and
[`infrastructure/database/repositoryImpl/PostgresSyncRepository.kt`](../src/main/kotlin/infrastructure/database/repositoryImpl/PostgresSyncRepository.kt):

1. **`COLLECTION_ONLY` now unions bookmarked and authored recipes.**
   `findCandidateRecipeIds`'s `"COLLECTION_ONLY"` branch matches
   `BookmarkedRecipeTable` rows **or** `RecipeTable.creator_id == userId`, so
   "my collection" means what the label says regardless of whether the user
   bothered to bookmark their own recipes. KDoc on
   `SyncRepository.findCandidateRecipeIds` updated to match.
   Regression test:
   `PostgresSyncRepositoryIntegrationTest.findCandidateRecipeIdsCollectionOnlyIncludesAuthoredAndBookmarked`
   (authored-but-unbookmarked, bookmarked-but-authored-by-another-user, and
   an untouched PUBLIC recipe from another user — only the first two should
   be candidates).
2. **`HIGH` variety falls back to round-robin once candidates run out**,
   instead of leaving days empty. Previously, a 5-recipe collection over a
   7-day `HIGH`-variety plan left 2 days with a null `dinnerRecipeId` purely
   because strict no-repeat had no headroom left — even though the pool
   itself was non-empty. `pickFrom`'s `HIGH` branch now falls back to
   `candidates[history.size % candidates.size]` (the same round-robin used by
   `LOW`) once every candidate has appeared once, so a plan is only ever
   partially filled when the candidate pool is empty, never merely for lack
   of variety headroom. Unit tests in
   `MealPlanGenerationServiceTest`: `HIGH variety with fewer candidates than
   days fills every slot via round-robin` and `HIGH variety with 5
   candidates over 7 days uses each once then round-robins`.

**End-to-end scenario this now supports**: register a user, create 5
recipes without bookmarking any, push a `DRAFT` plan with
`recipeSource: "COLLECTION_ONLY"`, `planLengthDays: 7`,
`varietyPreference: "HIGH"`, call `/generate`, pull — status comes back
`READY` with all 7 days filled (5 distinct recipes + 2 repeats), instead of
the previous empty-pool / partial-fill behavior.

## Phase 2.1 — Done: close the stale-bookmark leak in COLLECTION_ONLY

**Status: built (2026-08-20).** Diagnosed from a client-visible symptom: a
`COLLECTION_ONLY` plan's day 2 dinner referenced a recipe UUID that was
never in the user's local `recipes` table on Android — not soft-deleted,
just never delivered by any `/sync/pull`, and not present in the user's
`bookmarked_recipes` either. The plan itself came from the server
(`syncState=SYNCED`), so the dangling reference had to originate in
`findCandidateRecipeIds`.

**Root cause**: the `"COLLECTION_ONLY"` branch built its `bookmarked` set
from `BookmarkedRecipeTable` alone — no join back to `RecipeTable` — while
every other recipe-delivery query in this file (`findDeltaRecipes`'s pull
query, and this same function's own `INCLUDE_PUBLIC`/`else` branch) gates on
`deleted_at IS NULL AND (creator_id = userId OR privacy = 'PUBLIC')`.
Nothing in this codebase cleans up `bookmarked_recipes` when the bookmarked
recipe is later soft-deleted or its owner flips `privacy` away from
`PUBLIC` — so a bookmark can quietly outlive the recipe's accessibility to
the bookmarking user. The stale bookmark stayed a valid `COLLECTION_ONLY`
candidate forever, even though `/sync/pull` (bound to the same
creator-or-public predicate) would never actually deliver that recipe to
the client — producing a meal-plan slot the client can never resolve, no
matter how many times it syncs.

**Fix**, in
[`findCandidateRecipeIds`](../src/main/kotlin/infrastructure/database/repositoryImpl/PostgresSyncRepository.kt):
the bookmarked recipe IDs are now re-checked against `RecipeTable` with the
same `deleted_at IS NULL AND (creator_id = userId OR privacy = 'PUBLIC')`
predicate before being unioned with `authored`. KDoc on
`SyncRepository.findCandidateRecipeIds` updated to match. Regression test:
`PostgresSyncRepositoryIntegrationTest.findCandidateRecipeIdsCollectionOnlyExcludesStaleBookmarks`
(one bookmark whose recipe stays `PUBLIC`, one whose owner later flips it to
`PRIVATE`, one whose recipe is later soft-deleted — only the first should
remain a candidate).

**Audited**: `INCLUDE_PUBLIC` does not have an analogous gap. Its candidate
query applies the `deleted_at`/`creator_id`-or-`PUBLIC` predicate directly
against `RecipeTable` (see Phase 0 above) and never queries
`bookmarked_recipes` at all, so a stale or privacy-flipped bookmark can't
leak a recipe into an `INCLUDE_PUBLIC` plan the way it could for
`COLLECTION_ONLY`. Confirmed by
`PostgresSyncRepositoryIntegrationTest.findCandidateRecipeIdsIncludePublicExcludesInaccessibleAndStaleBookmarkedRecipes`:
a recipe the user bookmarked while `PUBLIC` and whose owner later flips to
`PRIVATE`, a never-bookmarked private recipe, and a soft-deleted public
recipe are all excluded; only the user's own (private) recipe and another
user's still-public recipe remain candidates.

## Phase 3 — Done: persist preference defaults

**Status: built.** The wizard already collects everything
`MealPlanPreferences` needs, per plan — there's no missing "onboarding"
step, just no persisted user-level default. Added a small
`UserPreferencesTable` + domain repository interface/impl (following this
repo's existing Clean Architecture conventions: interface in
`domain/repository`, impl in `infrastructure/database/repositoryImpl`)
capturing the same `preferencesJson` shape as `MealPlanTable.preferences`.

- **`UserPreferencesTable`**
  (`infrastructure/database/tables/UserPreferencesTable.kt`) — one row per
  user (`PrimaryKey(user_id)`), a single `preferences` text column (same
  JSON blob shape as `meal_plans.preferences`), and `updated_at`.
  `UserPreferencesRepository` (`domain/repository/UserPreferencesRepository.kt`)
  / `PostgresUserPreferencesRepository`
  (`infrastructure/database/repositoryImpl/PostgresUserPreferencesRepository.kt`)
  follow the same suspendTransaction/select-then-branch upsert pattern as
  `PostgresSyncRepository.upsertBookmark`.
- **Kept in sync automatically, no separate "save defaults" action**:
  `SyncService.processMealPlans` upserts the stored preferences from
  `plan.preferencesJson` immediately after every *accepted* meal-plan push
  (not on conflicts — a push that lost a conflict never actually took
  effect). So "the user's last plan" and "their stored defaults" are always
  the same thing, with zero new UI required for it on the client.
- **`GET /user/preferences`**
  (`presentation/routes/UserPreferencesRoutes.kt`), JWT-authenticated like
  every other private route: `200` with a `UserPreferencesResponse` body
  (`application/dto/UserPreferencesDto.kt`) when the user has pushed at
  least one plan, `204 No Content` when they haven't yet — absence is a
  normal first-time-user state, not an error, matching the "no missing
  onboarding step" framing above. Chose a dedicated REST endpoint over
  extending the sync push/pull protocol: every resource in
  `docs/sync-protocol.md` today is a delta/gap list keyed by UUID, and a
  singleton per-user record doesn't fit that shape or need its union
  semantics — a small new route keeps the well-tested sync core untouched.
- Tests: `PostgresUserPreferencesRepositoryIntegrationTest` (null before
  first upsert, overwrite-not-duplicate on a second upsert);
  `SyncServiceTest.pushingAMealPlanUpdatesStoredUserPreferences` /
  `conflictedMealPlanPushDoesNotUpdateStoredUserPreferences`;
  `UserPreferencesIntegrationTest` (401 unauthenticated, 204 before any
  plan, 200 with the exact fields from the last pushed plan).

**Explicitly out of scope for this phase** (client-side, in `jmuci/ChefAI`,
not this repo): actually wiring the wizard to call `GET /user/preferences`
and pre-fill its fields from the response.

## Phase 4 — Done: smarter deterministic ranking

**Status: built.** Still no ML/LLM. Three additional signals now bias which
candidate `pickFrom` sees first, on top of the existing variety logic —
never overriding it, and never leaving a slot unfilled that would
otherwise be filled:

1. **`leftoverFriendly` is finally consulted.** It was parsed since Phase 0
   but never read during assignment. Now, when true, candidates whose
   `servings` exceeds `servingsPerMeal` are shuffled ahead of the rest in
   `MealPlanGenerationService.leftoverAwareShuffle` — an ordering bias, not
   a filter, so recipes that would actually produce leftovers are favored
   early in the plan without excluding anything.
2. **Dominant-ingredient repetition is soft-avoided across consecutive
   days.** There's no per-recipe "dominant ingredient" column in this
   schema, and comparing raw `RecipeIngredientTable.quantity` across
   recipes is unreliable (units aren't normalized). Instead,
   `PostgresSyncRepository.findRecipeRankingMetadata` computes each
   recipe's dominant *food-group category* — the mode (most frequent, ties
   broken alphabetically) of `SourceClassificationTable.category` across
   its classified ingredients, joined manually via
   `IngredientTable.source_primary_id` (a plain column, not a typed FK, so
   this is 3 batched queries + an in-memory join rather than one Exposed
   join). A recipe with no classified ingredients simply isn't
   constrained. `MealPlanGenerationService.rankForDay` then soft-sorts a
   day's candidates so ones sharing the previous day's dominant category
   (for the same meal slot — dinner/lunch tracked independently) sort
   last.
3. **New plans weight against the user's recent past plans, not just their
   own history.** `PostgresSyncRepository.findRecentlyUsedRecipeIds`
   returns recipe IDs (dinner or lunch) from the user's last 3 `READY`
   plans (mirrors `MEDIUM` variety's existing hardcoded 3-day gap),
   excluding the plan currently being generated and soft-deleted plans.
   DRAFT/GENERATING plans don't count — only plans that actually reached
   `READY` reflect something the user was really served. `rankForDay`
   soft-deprioritizes these the same way as the category signal.

**Important scoping decision, found while testing**: signals 2 and 3 only
apply to `MEDIUM`/`HIGH` variety, not `LOW`. `LOW`'s
`candidates[history.size % candidates.size]` round-robin walks every list
position over the course of a plan, so re-sorting the candidate list per
day doesn't reliably bias `LOW`'s outcome the way it does for
`MEDIUM`/`HIGH`'s "try the preferred candidate first, fall back" logic —
it can even deterministically land on the *least*-preferred candidate for
some day/candidate-count combinations, which a test caught immediately.
`LOW`'s contract is "free repetition, no variety logic" — these are
additive refinements to the "try preferred first" step that only
`MEDIUM`/`HIGH` have, so `rankForDay` returns `LOW`'s candidates unchanged.

`pickFrom` itself was not touched — Phase 2's HIGH/MEDIUM/LOW logic is
exactly as it was; every new signal is a candidate-list reorder computed
in `assignRecipesToDays`/`rankForDay` before the unchanged `pickFrom` call,
keeping the blast radius minimal and Phase 2's tests untouched.

Tests: `MealPlanGenerationServiceTest` (leftoverFriendly bias, category
avoidance when an alternative exists, category repetition remains
soft/never-empty, recentlyUsedElsewhere deprioritization and its
soft/never-empty case, `LOW` variety's exemption);
`PostgresRecipeRankingIntegrationTest` (servings + mode-category
correctness including the unclassified-ingredient case;
`findRecentlyUsedRecipeIds` respecting the `READY`-only filter, the limit,
plan exclusion, and soft-deleted plans).

## Phase 5 — Future/optional: AI-assisted generation

The "AI-style" language already in `MealPlanGenerationService`'s doc
comment becomes literally true — e.g. an LLM call to theme a week, fill
gaps intelligently, or generate a plan description — with the Phase 0–4
deterministic logic kept as the fallback path. Explicitly deferred; will
need its own ADR (cost, latency, secret handling) wherever it's picked up.
