-- ChefAI Complete Database Schema
-- This script creates the entire database schema from scratch
-- Use this for fresh database installations

-- ============================================================================
-- STEP 1: DROP EXISTING TABLES (if starting fresh)
-- ============================================================================
-- Uncomment if you want to start completely fresh
-- DROP TABLE IF EXISTS refresh_tokens CASCADE;
-- DROP TABLE IF EXISTS recipe CASCADE;
-- DROP TABLE IF EXISTS users CASCADE;

-- ============================================================================
-- STEP 2: CREATE USERS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Users table indexes
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

COMMENT ON TABLE users IS 'User accounts for authentication and recipe ownership';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash of user password (cost factor 12)';

-- ============================================================================
-- STEP 3: CREATE RECIPE TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS recipe (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(200) NOT NULL,
    label VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    prep_time_mins INTEGER NOT NULL,
    recipe_url TEXT NOT NULL,
    image_url TEXT NOT NULL,
    image_url_thumbnail TEXT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraint
    CONSTRAINT fk_recipe_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

-- Recipe table indexes
CREATE INDEX IF NOT EXISTS idx_recipe_user_id ON recipe(user_id);
CREATE INDEX IF NOT EXISTS idx_recipe_is_public ON recipe(is_public);
CREATE INDEX IF NOT EXISTS idx_recipe_label ON recipe(label);
CREATE INDEX IF NOT EXISTS idx_recipe_created_at ON recipe(created_at);

COMMENT ON TABLE recipe IS 'User-created recipes with ownership and visibility control';
COMMENT ON COLUMN recipe.is_public IS 'Whether recipe is visible to other users';
COMMENT ON COLUMN recipe.user_id IS 'Owner of the recipe (foreign key to users.id)';

-- ============================================================================
-- STEP 4: CREATE REFRESH_TOKENS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    
    -- Foreign key constraint
    CONSTRAINT fk_refresh_tokens_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

-- Refresh tokens indexes (CRITICAL for security and performance)
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash 
    ON refresh_tokens(token_hash);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id 
    ON refresh_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at 
    ON refresh_tokens(expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_is_revoked 
    ON refresh_tokens(is_revoked) 
    WHERE is_revoked = FALSE;

COMMENT ON TABLE refresh_tokens IS 'Stores hashed refresh tokens with rotation and revocation support';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of the refresh token (never store plaintext)';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'Token expiration time (default: 30 days from creation)';
COMMENT ON COLUMN refresh_tokens.is_revoked IS 'Whether token has been revoked (for rotation or security)';

-- ============================================================================
-- STEP 5: INSERT SAMPLE DATA (Optional)
-- ============================================================================

-- Create a demo user (password is "Demo1234")
INSERT INTO users (id, email, username, password_hash, created_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440001',
    'demo@chefai.local',
    'DemoUser',
    '$2a$12$LQDHPwzzPWFERYtY3KfQaeFmIaM5b7YQ7GxJYqG.N7GnXvLqYG5Ai',
    CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;

-- Create a system user for migration purposes
INSERT INTO users (id, email, username, password_hash, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'system@chefai.local',
    'System',
    '$2a$12$placeholder.hash.not.for.real.use',
    CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;

-- Insert sample recipes
INSERT INTO recipe (uuid, user_id, title, label, description, prep_time_mins, recipe_url, image_url, image_url_thumbnail, is_public, created_at)
VALUES
(
    '3765597A-D982-42FC-9E42-41C788C69D5E',
    '550e8400-e29b-41d4-a716-446655440001',
    'Jamaican Jerk Chicken',
    'Caribbean',
    'This recipe is fragrant, fiery hot, and smoky all at once.',
    60,
    'https://www.foodandwine.com/recipe/jamaican-jerk-chicken',
    'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg',
    'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg',
    TRUE,
    CURRENT_TIMESTAMP
),
(
    '27655B7A-D982-42FC-9E42-41C788C69D5E',
    '550e8400-e29b-41d4-a716-446655440001',
    'Herb Roasted French Rack of Elk',
    'NewAmerican',
    'This recipe is fragrant, fiery hot, and smoky all at once.',
    45,
    'https://jhbuffalomeat.com/blogs/news/herb-roasted-french-rack-of-elk',
    'https://images.getrecipekit.com/20231115182037-plated-20with-20sauce.png?width=650&quality=90&',
    'https://images.getrecipekit.com/20231115182037-plated-20with-20sauce.png?width=650&quality=90&',
    TRUE,
    CURRENT_TIMESTAMP
),
(
    '1265597A-D982-42FC-9E42-41C788C69D5E',
    '550e8400-e29b-41d4-a716-446655440001',
    'Andalusian Gazpacho',
    'Spanish',
    'Refreshing and healthy cold vegetable soup',
    15,
    'https://www.javirecetas.com/gazpacho-receta-de-gazpacho-andaluz/',
    'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg',
    'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg',
    TRUE,
    CURRENT_TIMESTAMP
)
ON CONFLICT (uuid) DO NOTHING;

-- ============================================================================
-- STEP 6: VERIFICATION QUERIES
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
SELECT 'recipe', COUNT(*) FROM recipe
UNION ALL
SELECT 'refresh_tokens', COUNT(*) FROM refresh_tokens;

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
