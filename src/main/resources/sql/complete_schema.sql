-- ChefAI Complete Database Schema (Aligned with Client Schema v2)
-- This script creates the entire database schema from scratch based on client requirements

-- ============================================================================
-- STEP 1: DROP EXISTING TABLES (if starting fresh)
-- ============================================================================
-- Uncomment if you want to start completely fresh
-- DROP TABLE IF EXISTS refresh_tokens CASCADE;
-- DROP TABLE IF EXISTS recipe CASCADE;
-- DROP TABLE IF EXISTS users CASCADE;

-- ============================================================================
-- STEP 2: CREATE TABLES
-- ============================================================================
-- ===============================
-- USERS
-- ===============================
CREATE TABLE IF NOT EXISTS users (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    displayName TEXT NOT NULL,
    email TEXT NOT NULL,
    avatarUrl TEXT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_users_syncstate_updatedat ON users(syncState, updatedAt);

-- ===============================
-- ALLERGENS
-- ===============================
CREATE TABLE IF NOT EXISTS allergens (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    displayName TEXT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_allergens_syncstate_updatedat ON allergens(syncState, updatedAt);

-- ===============================
-- SOURCE_CLASSIFICATIONS
-- ===============================
CREATE TABLE IF NOT EXISTS source_classifications (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category TEXT NOT NULL,
    subcategory TEXT,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_source_classifications_syncstate_updatedat ON source_classifications(syncState, updatedAt);

-- ===============================
-- INGREDIENTS
-- ===============================
CREATE TABLE IF NOT EXISTS ingredients (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    displayName TEXT NOT NULL,
    allergenId UUID,
    sourcePrimaryId UUID,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL,
    CONSTRAINT fk_ingredients_allergen FOREIGN KEY (allergenId) REFERENCES allergens(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION,
    CONSTRAINT fk_ingredients_source_primary FOREIGN KEY (sourcePrimaryId) REFERENCES source_classifications(uuid) ON DELETE SET NULL ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_ingredients_allergenid ON ingredients(allergenId);
CREATE INDEX IF NOT EXISTS idx_ingredients_sourceprimaryid ON ingredients(sourcePrimaryId);
CREATE INDEX IF NOT EXISTS idx_ingredients_syncstate_updatedat ON ingredients(syncState, updatedAt);

-- ===============================
-- LABELS
-- ===============================
CREATE TABLE IF NOT EXISTS labels (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    displayName TEXT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_labels_syncstate_updatedat ON labels(syncState, updatedAt);

-- ===============================
-- TAGS
-- ===============================
CREATE TABLE IF NOT EXISTS tags (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    displayName TEXT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tags_syncstate_updatedat ON tags(syncState, updatedAt);

-- ===============================
-- RECIPES
-- ===============================
CREATE TABLE IF NOT EXISTS recipes (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    imageUrl TEXT NOT NULL,
    imageUrlThumbnail TEXT NOT NULL,
    prepTimeMinutes INTEGER NOT NULL,
    cookTimeMinutes INTEGER NOT NULL,
    servings INTEGER NOT NULL,
    creatorId UUID NOT NULL,
    recipeExternalUrl TEXT,
    privacy TEXT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL,
    CONSTRAINT fk_recipes_creator FOREIGN KEY (creatorId) REFERENCES users(uuid) ON DELETE CASCADE ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipes_creatorid ON recipes(creatorId);
CREATE INDEX IF NOT EXISTS idx_recipes_syncstate_updatedat ON recipes(syncState, updatedAt);

-- ===============================
-- RECIPE_INGREDIENTS (Many-to-many)
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_ingredients (
    recipeId UUID NOT NULL,
    ingredientId UUID NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    unit TEXT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL,
    PRIMARY KEY (recipeId, ingredientId),
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipeId) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_ingredients_ingredient FOREIGN KEY (ingredientId) REFERENCES ingredients(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION 
);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipeid ON recipe_ingredients(recipeId);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_ingredientid ON recipe_ingredients(ingredientId);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_syncstate_updatedat ON recipe_ingredients(syncState, updatedAt);

-- ===============================
-- RECIPE_LABELS (Many-to-many)
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_labels (
    recipeId UUID NOT NULL,
    labelId UUID NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL,
    PRIMARY KEY (recipeId, labelId),
    CONSTRAINT fk_recipe_labels_recipe FOREIGN KEY (recipeId) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_labels_label FOREIGN KEY (labelId) REFERENCES labels(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_labels_labelid ON recipe_labels(labelId);
CREATE INDEX IF NOT EXISTS idx_recipe_labels_syncstate_updatedat ON recipe_labels(syncState, updatedAt);

-- ===============================
-- RECIPE_TAGS (Many-to-many)
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_tags (
    recipeId UUID NOT NULL,
    tagId UUID NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL,
    PRIMARY KEY (recipeId, tagId),
    CONSTRAINT fk_recipe_tags_recipe FOREIGN KEY (recipeId) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION,
    CONSTRAINT fk_recipe_tags_tag FOREIGN KEY (tagId) REFERENCES tags(uuid) ON DELETE RESTRICT ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_tags_tagid ON recipe_tags(tagId);
CREATE INDEX IF NOT EXISTS idx_recipe_tags_syncstate_updatedat ON recipe_tags(syncState, updatedAt);

-- ===============================
-- RECIPE_STEPS
-- ===============================
CREATE TABLE IF NOT EXISTS recipe_steps (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipeId UUID NOT NULL,
    orderIndex INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    updatedAt BIGINT NOT NULL,
    deletedAt BIGINT,
    syncState TEXT NOT NULL,
    serverUpdatedAt TIMESTAMP NOT NULL,
    CONSTRAINT fk_recipe_steps_recipe FOREIGN KEY (recipeId) REFERENCES recipes(uuid) ON DELETE CASCADE ON UPDATE NO ACTION
);
CREATE INDEX IF NOT EXISTS idx_recipe_steps_recipeid ON recipe_steps(recipeId);
CREATE INDEX IF NOT EXISTS idx_recipe_steps_syncstate_updatedat ON recipe_steps(syncState, updatedAt);

-- ============================================================================
-- STEP 3: CLEANUP: Remove legacy tables not found in client schema
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS recipe CASCADE;
DROP TABLE IF EXISTS users CASCADE;
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
