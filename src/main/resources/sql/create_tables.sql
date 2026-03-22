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
    CONSTRAINT fk_recipes_creator FOREIGN KEY (creator_id) REFERENCES users(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_recipes_creator_id        ON recipes(creator_id);
CREATE INDEX IF NOT EXISTS idx_recipes_server_updated_at ON recipes(server_updated_at);
CREATE INDEX IF NOT EXISTS idx_recipes_deleted_at_not_null ON recipes(deleted_at) WHERE deleted_at IS NOT NULL;

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
