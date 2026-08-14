# Meal Plan Generation — Incremental Roadmap

**Status**: Living document — update as phases complete.
**Last updated**: 2026-08-14

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
- Day assignment: `HIGH` variety never repeats a recipe (partial-fills once
  candidates run out); `MEDIUM` allows a repeat after a 3-day gap; `LOW`
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

Only 106 recipes exist today, all from `src/main/resources/sql/seed.sql`.
Generation quality can't really be evaluated until the catalog is bigger.
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

## Phase 2 — Fix the "my recipes only" gap

The client's `RecipeSource.COLLECTION_ONLY` is labeled **"My collection
only."** On the backend, `findCandidateRecipeIds` implements
`COLLECTION_ONLY` as strictly `BookmarkedRecipeTable` rows. Neither the
backend (`RecipesService`) nor the Android client auto-bookmarks a recipe
when its creator authors it — so a user who writes 5 recipes but never
explicitly bookmarks them gets an **empty candidate pool** under "my
collection only," silently contradicting the wizard's own label. This is a
concrete bug to fix, not a hypothetical:

- **Recommended fix**: make `COLLECTION_ONLY` match `creator_id == userId`
  **or** bookmarked, so "my collection" means what the label says. (Splitting
  into two explicit modes — authored-only vs. bookmarked-only — is a valid
  alternative if product intent wants them distinguished; default to the
  combined interpretation unless told otherwise.)
- Add/extend test coverage for a small-pool case (exactly 5 own recipes
  over a 5-day plan) across each `VarietyPreference`, confirming
  `LOW`/`MEDIUM` round-robin correctly and deciding whether `HIGH` should
  also fall back to round-robin instead of leaving slots empty when the
  pool is smaller than the plan length.

## Phase 3 — Persist preference defaults

The wizard already collects everything `MealPlanPreferences` needs, per
plan — there's no missing "onboarding" step, just no persisted user-level
default. Add a small `UserPreferencesTable` + domain model + repository
interface/impl (following this repo's existing Clean Architecture
conventions: interface in `domain/repository`, impl in
`infrastructure/database/repositoryImpl`) capturing the same fields as
`MealPlanPreferences`. New meal plans default from it; the wizard pre-fills
from the user's last plan instead of starting blank every time.

## Phase 4 — Smarter deterministic ranking

Still no ML/LLM. Improvements to `assignRecipesToDays`:
- Actually use `leftoverFriendly` (currently parsed in `parsePreferences`
  but never consulted during assignment).
- Avoid repeating dominant ingredients across consecutive days.
- Weight against recipes used in the user's recent *past* meal plans, not
  just within the current plan's own history.

## Phase 5 — Future/optional: AI-assisted generation

The "AI-style" language already in `MealPlanGenerationService`'s doc
comment becomes literally true — e.g. an LLM call to theme a week, fill
gaps intelligently, or generate a plan description — with the Phase 0–4
deterministic logic kept as the fallback path. Explicitly deferred; will
need its own ADR (cost, latency, secret handling) wherever it's picked up.
