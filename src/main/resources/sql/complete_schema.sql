-- ChefAI Complete Database Schema (Aligned with Client Schema v2)
-- This script creates the entire database schema from scratch based on client requirements

-- =========================================================================
-- STEP 1: DROP EXISTING TABLES (if starting fresh)
-- =========================================================================
--- DROP TABLE IF EXISTS refresh_tokens CASCADE;
--- DROP TABLE IF EXISTS recipes CASCADE;
--- DROP TABLE IF EXISTS users CASCADE;
--- DROP TABLE IF EXISTS allergens CASCADE;
--- DROP TABLE IF EXISTS source_classifications CASCADE;
--- DROP TABLE IF EXISTS labels CASCADE;
--- DROP TABLE IF EXISTS tags CASCADE;
--- DROP TABLE IF EXISTS recipe_ingredients CASCADE;
--- DROP TABLE IF EXISTS recipe_labels CASCADE;
--- DROP TABLE IF EXISTS recipe_tags CASCADE;
--- DROP TABLE IF EXISTS recipe_steps CASCADE;

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
    display_name TEXT NOT NULL,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    avatar_url TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_users_sync_state_updated_at ON users(sync_state, updated_at);

-- ===============================
-- REFRESH_TOKENS
-- ===============================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(uuid) ON DELETE CASCADE ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_sync_state_updated_at ON refresh_tokens(sync_state, updated_at);

-- ===============================
-- ALLERGENS
-- ===============================
CREATE TABLE IF NOT EXISTS allergens (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_allergens_sync_state_updated_at ON allergens(sync_state, updated_at);

-- ===============================
-- SOURCE_CLASSIFICATIONS
-- ===============================
CREATE TABLE IF NOT EXISTS source_classifications (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category TEXT NOT NULL,
    subcategory TEXT,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_source_classifications_sync_state_updated_at ON source_classifications(sync_state, updated_at);

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
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ingredients_allergen FOREIGN KEY (allergen_id) REFERENCES allergens(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION,
    CONSTRAINT fk_ingredients_source_primary FOREIGN KEY (source_primary_id) REFERENCES source_classifications(uuid) ON DELETE SET NULL ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_ingredients_allergen_id ON ingredients(allergen_id);
CREATE INDEX IF NOT EXISTS idx_ingredients_source_primary_id ON ingredients(source_primary_id);
CREATE INDEX IF NOT EXISTS idx_ingredients_sync_state_updated_at ON ingredients(sync_state, updated_at);

-- ===============================
-- LABELS
-- ===============================
CREATE TABLE IF NOT EXISTS labels (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_labels_sync_state_updated_at ON labels(sync_state, updated_at);

-- ===============================
-- TAGS
-- ===============================
CREATE TABLE IF NOT EXISTS tags (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name TEXT NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tags_sync_state_updated_at ON tags(sync_state, updated_at);

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
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_recipes_creator FOREIGN KEY (creator_id) REFERENCES users(uuid) ON DELETE CASCADE ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipes_creator_id ON recipes(creator_id);
CREATE INDEX IF NOT EXISTS idx_recipes_sync_state_updated_at ON recipes(sync_state, updated_at);


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
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (recipe_id, ingredient_id),
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_ingredients_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe_id ON recipe_ingredients(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_ingredient_id ON recipe_ingredients(ingredient_id);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_sync_state_updated_at ON recipe_ingredients(sync_state, updated_at);

-- ===============================
-- RECIPE_LABELS (Many-to-many)
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_labels (
    recipe_id UUID NOT NULL,
    label_id UUID NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (recipe_id, label_id),
    CONSTRAINT fk_recipe_labels_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_labels_label FOREIGN KEY (label_id) REFERENCES labels(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_labels_label_id ON recipe_labels(label_id);
CREATE INDEX IF NOT EXISTS idx_recipe_labels_sync_state_updated_at ON recipe_labels(sync_state, updated_at);

-- ===============================
-- RECIPE_TAGS (Many-to-many)
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_tags (
    recipe_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    updated_at BIGINT NOT NULL,
    deleted_at BIGINT,
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (recipe_id, tag_id),
    CONSTRAINT fk_recipe_tags_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_tags_tag_id ON recipe_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_recipe_tags_sync_state_updated_at ON recipe_tags(sync_state, updated_at);

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
    sync_state TEXT NOT NULL,
    server_updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_recipe_steps_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_steps_recipe_id ON recipe_steps(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_steps_sync_state_updated_at ON recipe_steps(sync_state, updated_at);

-- ============================================================================
-- STEP 3: CLEANUP: Remove legacy tables not found in client schema
-- End of new schema based on client v2

-- ============================================================================
-- STEP 4: INSERT SAMPLE DATA (Optional)
-- ============================================================================

-- Create a demo user (password is "Demo1234")
-- INSERT INTO users (id, email, username, password_hash, created_at)
-- VALUES (
--     '550e8400-e29b-41d4-a716-446655440001',
--     'demo@chefai.local',
--     'DemoUser',
--     '$2a$12$LQDHPwzzPWFERYtY3KfQaeFmIaM5b7YQ7GxJYqG.N7GnXvLqYG5Ai',
--     CURRENT_TIMESTAMP
-- )
-- ON CONFLICT (email) DO NOTHING;

-- Create a system user for migration purposes
-- INSERT INTO users (id, email, username, password_hash, created_at)
-- VALUES (
--     '00000000-0000-0000-0000-000000000001',
--     'system@chefai.local',
--     'System',
--     '$2a$12$placeholder.hash.not.for.real.use',
--     CURRENT_TIMESTAMP
-- )
-- ON CONFLICT (email) DO NOTHING;

-- Insert sample recipes
-- INSERT INTO recipe (uuid, user_id, title, label, description, prep_time_mins, recipe_url, image_url, image_url_thumbnail, is_public, created_at)
-- VALUES
-- (
--     '3765597A-D982-42FC-9E42-41C788C69D5E',
--     '550e8400-e29b-41d4-a716-446655440001',
--     'Jamaican Jerk Chicken',
--     'Caribbean',
--     'This recipe is fragrant, fiery hot, and smoky all at once.',
--     60,
--     'https://www.foodandwine.com/recipe/jamaican-jerk-chicken',
--     'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg',
--     'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg',
--     TRUE,
--     CURRENT_TIMESTAMP
-- ),
-- (
--     '27655B7A-D982-42FC-9E42-41C788C69D5E',
--     '550e8400-e29b-41d4-a716-446655440001',
--     'Herb Roasted French Rack of Elk',
--     'NewAmerican',
--     'This recipe is fragrant, fiery hot, and smoky all at once.',
--     45,
--     'https://jhbuffalomeat.com/blogs/news/herb-roasted-french-rack-of-elk',
--     'https://images.getrecipekit.com/20231115182037-plated-20with-20sauce.png?width=650&quality=90&',
--     'https://images.getrecipekit.com/20231115182037-plated-20with-20sauce.png?width=650&quality=90&',
--     TRUE,
--     CURRENT_TIMESTAMP
-- ),
-- (
--     '1265597A-D982-42FC-9E42-41C788C69D5E',
--     '550e8400-e29b-41d4-a716-446655440001',
--     'Andalusian Gazpacho',
--     'Spanish',
--     'Refreshing and healthy cold vegetable soup',
--     15,
--     'https://www.javirecetas.com/gazpacho-receta-de-gazpacho-andaluz/',
--     'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg',
--     'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg',
--     TRUE,
--     CURRENT_TIMESTAMP
-- )
-- ON CONFLICT (uuid) DO NOTHING;

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
