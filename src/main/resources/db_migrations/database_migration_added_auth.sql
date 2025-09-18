-- ChefAI Authentication Migration Script
-- This script adds authentication and authorization support to the existing database

-- Step 1: Create users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Step 2: Add authentication/authorization columns to recipe table
ALTER TABLE recipe ADD COLUMN IF NOT EXISTS user_id VARCHAR(36);
ALTER TABLE recipe ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT FALSE;

-- Step 3: Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_recipe_user_id ON recipe(user_id);
CREATE INDEX IF NOT EXISTS idx_recipe_is_public ON recipe(is_public);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Step 4: Update existing recipes to have a default user
-- Note: You may want to customize this based on your needs
-- This creates a "system" user and assigns all existing recipes to it
INSERT INTO users (id, email, username, password_hash, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'system@chefai.local',
    'System',
    '$2a$12$placeholder.hash.not.for.real.use',
    CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;

UPDATE recipe 
SET user_id = 'system-user-id'
WHERE user_id IS NULL OR user_id = '';

-- Step 5: Make user_id NOT NULL after populating existing records
ALTER TABLE recipe ALTER COLUMN user_id SET NOT NULL;

-- Verification queries (uncomment to run)
-- SELECT COUNT(*) as total_users FROM users;
-- SELECT COUNT(*) as total_recipes FROM recipe;
-- SELECT COUNT(*) as recipes_with_user FROM recipe WHERE user_id IS NOT NULL;
-- SELECT COUNT(*) as public_recipes FROM recipe WHERE is_public = TRUE;
