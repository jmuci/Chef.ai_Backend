-- ChefAI: add full-text search support to recipes.
--
-- Adds a generated `search_vector tsvector` column over title + description (weighted 'A'/'C' so a
-- title match ranks above a description-only one) plus a GIN index over it, used by
-- GET /api/v1/recipes/search. Run this once against any database created before this feature
-- shipped — idempotent (both statements are IF NOT EXISTS), safe to run more than once.
--
-- The two-argument to_tsvector(regconfig, text) form is mandatory: it's the only IMMUTABLE
-- overload (verified against a live PG 16 via pg_proc.provolatile), which GENERATED ALWAYS AS
-- ... STORED requires. The single-argument form is only STABLE (depends on the session's
-- default_text_search_config) and Postgres rejects it inside a generated column.
--
-- Mirrored in create_tables.sql (fresh installs) and infrastructure/database/DatabaseInit.kt
-- (applied automatically at boot) — there is no migration runner in this project, so this file
-- exists only for whoever needs to apply the change to a running database by hand ahead of a
-- deploy, or is restoring/inspecting a dump taken before this shipped.

ALTER TABLE recipes ADD COLUMN IF NOT EXISTS search_vector tsvector
  GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'C')
  ) STORED;

CREATE INDEX IF NOT EXISTS idx_recipes_search_vector ON recipes USING GIN (search_vector);
