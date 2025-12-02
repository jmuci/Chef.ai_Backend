-- ChefAI Complete Database Schema (Aligned with Client Schema v2)
-- This script creates the entire database schema from scratch based on client requirements

-- =========================================================================
-- STEP 1: DROP EXISTING TABLES (if starting fresh)
-- =========================================================================
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS recipes CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS allergens CASCADE;
DROP TABLE IF EXISTS source_classifications CASCADE;
DROP TABLE IF EXISTS labels CASCADE;
DROP TABLE IF EXISTS tags CASCADE;
DROP TABLE IF EXISTS recipe_ingredients CASCADE;
DROP TABLE IF EXISTS recipe_labels CASCADE;
DROP TABLE IF EXISTS recipe_tags CASCADE;
DROP TABLE IF EXISTS recipe_steps CASCADE;

--- DROP DATABASE chefai_db
--- CREATE DATABASE chefai_db
-- =========================================================================
-- STEP 2: CREATE TABLES
-- =========================================================================
-- ===============================
-- USERS
-- ===============================
CREATE TABLE IF NOT EXISTS users (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_name TEXT NOT NULL,
    display_name TEXT DEFAULT NULL,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    avatar_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- REFRESH_TOKENS
-- ===============================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash TEXT NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP DEFAULT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(uuid) ON DELETE CASCADE ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ===============================
-- ALLERGENS
-- ===============================
CREATE TABLE IF NOT EXISTS allergens (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- SOURCE_CLASSIFICATIONS
-- ===============================
CREATE TABLE IF NOT EXISTS source_classifications (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category TEXT NOT NULL,
    subcategory TEXT,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- INGREDIENTS
-- ===============================
CREATE TABLE IF NOT EXISTS ingredients (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name TEXT NOT NULL,
    allergen_id UUID,
    source_primary_id UUID,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ingredients_allergen FOREIGN KEY (allergen_id) REFERENCES allergens(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION,
    CONSTRAINT fk_ingredients_source_primary FOREIGN KEY (source_primary_id) REFERENCES source_classifications(uuid) ON DELETE SET NULL ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_ingredients_allergen_id ON ingredients(allergen_id);
CREATE INDEX IF NOT EXISTS idx_ingredients_source_primary_id ON ingredients(source_primary_id);

-- ===============================
-- LABELS
-- ===============================
CREATE TABLE IF NOT EXISTS labels (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- TAGS
-- ===============================
CREATE TABLE IF NOT EXISTS tags (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ===============================
-- RECIPES
-- ===============================
CREATE TABLE IF NOT EXISTS recipes (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    image_url TEXT NOT NULL,
    image_url_thumbnail TEXT NOT NULL,
    prep_time_minutes INTEGER NOT NULL,
    cook_time_minutes INTEGER NOT NULL,
    servings INTEGER NOT NULL,
    creator_id UUID NOT NULL,
    recipe_external_url TEXT,
    privacy TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipes_creator FOREIGN KEY (creator_id) REFERENCES users(uuid) ON DELETE CASCADE ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipes_creator_id ON recipes(creator_id);


-- ===============================
-- RECIPE_INGREDIENTS (Many-to-many)
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_ingredients (
    recipe_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    unit TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recipe_id, ingredient_id),
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_ingredients_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe_id ON recipe_ingredients(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_ingredient_id ON recipe_ingredients(ingredient_id);

-- ===============================
-- RECIPE_LABELS (Many-to-many)
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_labels (
    recipe_id UUID NOT NULL,
    label_id UUID NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recipe_id, label_id),
    CONSTRAINT fk_recipe_labels_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_labels_label FOREIGN KEY (label_id) REFERENCES labels(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_labels_label_id ON recipe_labels(label_id);

-- ===============================
-- RECIPE_TAGS (Many-to-many)
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_tags (
    recipe_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recipe_id, tag_id),
    CONSTRAINT fk_recipe_tags_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_tags_tag_id ON recipe_tags(tag_id);

-- ===============================
-- RECIPE_STEPS
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_steps (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipe_id UUID NOT NULL,
    order_index INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    server_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipe_steps_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_steps_recipe_id ON recipe_steps(recipe_id);

-- ============================================================================
-- STEP 3: CLEANUP: Remove legacy tables not found in client schema
-- End of new schema based on client v2


-- ============================================================================
-- STEP 5: VERIFICATION QUERIES
-- ============================================================================

-- Show all tables
SELECT tablename
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename;

-- Show all indexes
SELECT
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

-- Show foreign keys
SELECT
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name,
    tc.constraint_name
FROM information_schema.table_constraints AS tc
    JOIN information_schema.key_column_usage AS kcu
      ON tc.constraint_name = kcu.constraint_name
    JOIN information_schema.constraint_column_usage AS ccu
      ON ccu.constraint_name = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public'
ORDER BY tc.table_name;

-- Count records
SELECT 'users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'refresh_tokens', COUNT(*) FROM refresh_tokens
UNION ALL
SELECT 'allergens', COUNT(*) FROM allergens
UNION ALL
SELECT 'source_classifications', COUNT(*) FROM source_classifications
UNION ALL
SELECT 'ingredients', COUNT(*) FROM ingredients
UNION ALL
SELECT 'labels', COUNT(*) FROM labels
UNION ALL
SELECT 'tags', COUNT(*) FROM tags
UNION ALL
SELECT 'recipes', COUNT(*) FROM recipes
UNION ALL
SELECT 'recipe_ingredients', COUNT(*) FROM recipe_ingredients
UNION ALL
SELECT 'recipe_labels', COUNT(*) FROM recipe_labels
UNION ALL
SELECT 'recipe_tags', COUNT(*) FROM recipe_tags
UNION ALL
SELECT 'recipe_steps', COUNT(*) FROM recipe_steps;

-- ============================================================================
-- COMPLETE
-- ============================================================================
-- Schema creation complete!
-- 
-- Default demo user:
--   Email: demo@chefai.local
--   Password: Demo1234
--
-- To create a new user via API:
--   POST /auth/register
--   {
--     "email": "your@email.com",
--     "username": "YourName",
--     "password": "SecurePass123"
--   }
