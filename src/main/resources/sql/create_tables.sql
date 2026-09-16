-- ChefAI Database Schema
-- Run this to create all tables from scratch.
-- Use drop_tables.sql first if you need a clean slate.

-- ===============================
-- USERS
-- ===============================
CREATE TABLE IF NOT EXISTS users (
    uuid          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_name     TEXT NOT NULL,
    display_name  TEXT NOT NULL DEFAULT '',
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    avatar_url    TEXT NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- REFRESH_TOKENS
-- ===============================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    uuid       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL,
    token_hash TEXT NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP DEFAULT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ===============================
-- HOUSEHOLDS
-- ===============================
CREATE TABLE IF NOT EXISTS households (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    owner_id    UUID NOT NULL REFERENCES users(uuid) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_households_owner_id ON households(owner_id);

-- ===============================
-- HOUSEHOLD_MEMBERS
-- ===============================
CREATE TABLE IF NOT EXISTS household_members (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id      UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
    role              TEXT NOT NULL CHECK (role IN ('OWNER','MEMBER')),
    status            TEXT NOT NULL CHECK (status IN ('ACTIVE','REMOVED')),
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    removed_at        TIMESTAMPTZ NULL,
    server_removed_at TIMESTAMPTZ NULL
);
-- At most one ACTIVE household per user. Must be partial (WHERE status = 'ACTIVE'): REMOVED rows
-- are kept as the tombstone source, and a plain unique index would permanently block rejoining a
-- household after leaving one. Exposed cannot express a filtered index, so this is also
-- hand-written in DatabaseInit.kt's createHouseholdConstraintsIfMissing() — the two must stay in
-- sync by hand, same category as the recipes.search_vector index.
CREATE UNIQUE INDEX IF NOT EXISTS idx_household_members_one_active_per_user
    ON household_members(user_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_household_members_household_id ON household_members(household_id);
CREATE INDEX IF NOT EXISTS idx_household_members_server_removed_at ON household_members(server_removed_at)
    WHERE server_removed_at IS NOT NULL;

-- ===============================
-- HOUSEHOLD_INVITES
-- ===============================
CREATE TABLE IF NOT EXISTS household_invites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    UUID NOT NULL REFERENCES households(id) ON DELETE CASCADE,
    created_by      UUID NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,  -- sha256(raw token); raw token NEVER stored
    invitee_user_id UUID NULL REFERENCES users(uuid) ON DELETE CASCADE,
    invitee_email   TEXT NULL,                     -- denormalized, display/audit only
    single_use      BOOLEAN NOT NULL DEFAULT TRUE,
    max_uses        INTEGER NULL,
    use_count       INTEGER NOT NULL DEFAULT 0,
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_by     UUID NULL REFERENCES users(uuid) ON DELETE SET NULL,
    accepted_at     TIMESTAMPTZ NULL,
    revoked_at      TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_household_invites_household_id ON household_invites(household_id);
CREATE INDEX IF NOT EXISTS idx_household_invites_invitee_user_id ON household_invites(invitee_user_id)
    WHERE invitee_user_id IS NOT NULL;

-- ===============================
-- ALLERGENS
-- ===============================
CREATE TABLE IF NOT EXISTS allergens (
    uuid              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name      TEXT NOT NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- SOURCE_CLASSIFICATIONS
-- ===============================
CREATE TABLE IF NOT EXISTS source_classifications (
    uuid              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category          TEXT NOT NULL,
    subcategory       TEXT,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- INGREDIENTS
-- ===============================
CREATE TABLE IF NOT EXISTS ingredients (
    uuid              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name      TEXT NOT NULL,
    allergen_id       UUID,
    source_primary_id UUID,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ingredients_allergen      FOREIGN KEY (allergen_id)       REFERENCES allergens(uuid)              ON DELETE RESTRICT,
    CONSTRAINT fk_ingredients_source_primary FOREIGN KEY (source_primary_id) REFERENCES source_classifications(uuid) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_ingredients_allergen_id      ON ingredients(allergen_id);
CREATE INDEX IF NOT EXISTS idx_ingredients_source_primary_id ON ingredients(source_primary_id);

-- ===============================
-- LABELS
-- ===============================
CREATE TABLE IF NOT EXISTS labels (
    uuid              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name      TEXT NOT NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- TAGS
-- ===============================
CREATE TABLE IF NOT EXISTS tags (
    uuid              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name      TEXT NOT NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- RECIPES
-- ===============================
CREATE TABLE IF NOT EXISTS recipes (
    uuid                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    image_url           TEXT NOT NULL,
    image_url_thumbnail TEXT NOT NULL,
    prep_time_minutes   INTEGER NOT NULL,
    cook_time_minutes   INTEGER NOT NULL,
    servings            INTEGER NOT NULL,
    creator_id          UUID NOT NULL,
    recipe_external_url TEXT,
    privacy             TEXT NOT NULL,
    updated_at          BIGINT NOT NULL,
    deleted_at          BIGINT,
    server_updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    image_blob_id       TEXT,
    -- Full-text search over title + description, used by GET /api/v1/recipes/search. Weighted
    -- 'A'/'C' so a title match ranks above a description-only match. The two-argument
    -- to_tsvector(regconfig, text) form is required here: it's the only IMMUTABLE overload
    -- (verified against a live PG 16 via pg_proc.provolatile), which GENERATED ALWAYS AS ...
    -- STORED requires. Deliberately not declared on the Exposed RecipeTable object — see the
    -- comment there.
    search_vector       tsvector GENERATED ALWAYS AS (
                             setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
                             setweight(to_tsvector('english', coalesce(description, '')), 'C')
                         ) STORED,
    CONSTRAINT fk_recipes_creator FOREIGN KEY (creator_id) REFERENCES users(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_recipes_creator_id        ON recipes(creator_id);
CREATE INDEX IF NOT EXISTS idx_recipes_server_updated_at ON recipes(server_updated_at);
CREATE INDEX IF NOT EXISTS idx_recipes_deleted_at_not_null ON recipes(deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_recipes_search_vector ON recipes USING GIN (search_vector);

-- ===============================
-- IMAGE_BLOBS
-- ===============================
-- Recipe hero image bytes, scoped per-user (not deduplicated across users).
-- recipes.image_blob_id stores the content_hash directly (an opaque change-token),
-- not a foreign key to this table's id — see docs/recipe-image-architecture.md.
CREATE TABLE IF NOT EXISTS image_blobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID    NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
    content_hash        TEXT    NOT NULL,
    provenance          TEXT    NOT NULL,
    mime_type           TEXT    NOT NULL,
    byte_size           BIGINT  NOT NULL,
    storage_key         TEXT    NOT NULL,
    created_at          BIGINT  NOT NULL,
    unreferenced_since  BIGINT  NULL,
    UNIQUE (user_id, content_hash)
);
CREATE INDEX IF NOT EXISTS idx_image_blobs_user_id            ON image_blobs(user_id);
CREATE INDEX IF NOT EXISTS idx_image_blobs_unreferenced_since ON image_blobs(unreferenced_since)
    WHERE unreferenced_since IS NOT NULL;

-- ===============================
-- RECIPE_INGREDIENTS
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_ingredients (
    recipe_id         UUID NOT NULL,
    ingredient_id     UUID NOT NULL,
    quantity          DOUBLE PRECISION NOT NULL,
    unit              TEXT NOT NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recipe_id, ingredient_id),
    CONSTRAINT fk_recipe_ingredients_recipe     FOREIGN KEY (recipe_id)     REFERENCES recipes(uuid)     ON DELETE CASCADE,
    CONSTRAINT fk_recipe_ingredients_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(uuid) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe_id     ON recipe_ingredients(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_ingredient_id ON recipe_ingredients(ingredient_id);

-- ===============================
-- RECIPE_LABELS
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_labels (
    recipe_id         UUID NOT NULL,
    label_id          UUID NOT NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recipe_id, label_id),
    CONSTRAINT fk_recipe_labels_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_labels_label  FOREIGN KEY (label_id)  REFERENCES labels(uuid)  ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_recipe_labels_label_id ON recipe_labels(label_id);

-- ===============================
-- RECIPE_TAGS
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_tags (
    recipe_id         UUID NOT NULL,
    tag_id            UUID NOT NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recipe_id, tag_id),
    CONSTRAINT fk_recipe_tags_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_tags_tag    FOREIGN KEY (tag_id)    REFERENCES tags(uuid)    ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_recipe_tags_tag_id ON recipe_tags(tag_id);

-- ===============================
-- RECIPE_STEPS
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_steps (
    uuid              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id         UUID NOT NULL,
    order_index       INTEGER NOT NULL,
    instruction       TEXT NOT NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipe_steps_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_recipe_steps_recipe_id ON recipe_steps(recipe_id);

-- ===============================
-- BOOKMARKED_RECIPES
-- ===============================
CREATE TABLE IF NOT EXISTS bookmarked_recipes (
    user_id           UUID NOT NULL,
    recipe_id         UUID NOT NULL,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMPTZ,
    PRIMARY KEY (user_id, recipe_id),
    CONSTRAINT fk_bookmarked_user   FOREIGN KEY (user_id)   REFERENCES users(uuid)   ON DELETE CASCADE,
    CONSTRAINT fk_bookmarked_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_bookmarked_recipes_server_updated_at ON bookmarked_recipes(server_updated_at);

-- ===============================
-- MEAL_PLANS
-- ===============================
-- Was missing from this file entirely (only ever created via Exposed's
-- createMissingTablesAndColumns) — backfilled here for fresh-install parity. household_id shares
-- a household's meal plans with every active member — see docs/household-architecture.md.
CREATE TABLE IF NOT EXISTS meal_plans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
    household_id      UUID NULL REFERENCES households(id) ON DELETE SET NULL,
    name              TEXT NOT NULL,
    status            TEXT NOT NULL,
    preferences       TEXT NOT NULL,
    created_at        BIGINT NOT NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_meal_plans_user_id ON meal_plans(user_id);
CREATE INDEX IF NOT EXISTS idx_meal_plans_household_id ON meal_plans(household_id) WHERE household_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_meal_plans_server_updated_at ON meal_plans(server_updated_at);

-- ===============================
-- MEAL_PLAN_DAYS
-- ===============================
-- Also backfilled — same staleness as meal_plans above.
CREATE TABLE IF NOT EXISTS meal_plan_days (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_plan_id      UUID NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    day_index         INTEGER NOT NULL,
    dinner_recipe_id  UUID NULL REFERENCES recipes(uuid) ON DELETE SET NULL,
    lunch_recipe_id   UUID NULL REFERENCES recipes(uuid) ON DELETE SET NULL,
    UNIQUE (meal_plan_id, day_index)
);
CREATE INDEX IF NOT EXISTS idx_meal_plan_days_meal_plan_id ON meal_plan_days(meal_plan_id);

-- ===============================
-- USER_PREFERENCES
-- ===============================
-- Also backfilled — same staleness as meal_plans above.
CREATE TABLE IF NOT EXISTS user_preferences (
    user_id     UUID PRIMARY KEY REFERENCES users(uuid) ON DELETE CASCADE,
    preferences TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

-- ===============================
-- GROCERY_LIST_ITEM_CHECKS
-- ===============================
-- Keyed by (meal_plan_id, item_key) rather than a surrogate id: the item itself only exists as a
-- client-derived opaque string, there's nothing else to key it by. checked is an explicit boolean,
-- not a tombstone-on-uncheck — see docs/sync-protocol.md's Grocery List section.
CREATE TABLE IF NOT EXISTS grocery_list_item_checks (
    meal_plan_id      UUID NOT NULL REFERENCES meal_plans(id) ON DELETE CASCADE,
    item_key          TEXT NOT NULL,
    checked           BOOLEAN NOT NULL,
    checked_by        UUID NULL REFERENCES users(uuid) ON DELETE SET NULL,
    updated_at        BIGINT NOT NULL,
    deleted_at        BIGINT NULL,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (meal_plan_id, item_key)
);
CREATE INDEX IF NOT EXISTS idx_grocery_list_item_checks_server_updated_at
    ON grocery_list_item_checks(server_updated_at);
