-- ============================================================================
-- ChefAI Fresh Database Installation
-- This script DROPS and recreates everything from scratch
-- WARNING: This will DELETE ALL DATA!
-- ============================================================================

-- ============================================================================
-- STEP 1: DROP ALL EXISTING TABLES
-- ============================================================================
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS recipe CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ============================================================================
-- STEP 2: CREATE USERS TABLE
-- ============================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);

COMMENT ON TABLE users IS 'User accounts for authentication and recipe ownership';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash of user password (cost factor 12)';

-- ============================================================================
-- STEP 3: CREATE RECIPE TABLE
-- ============================================================================
CREATE TABLE recipe (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    label VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    prep_time_mins INTEGER NOT NULL,
    recipe_url TEXT NOT NULL,
    image_url TEXT NOT NULL,
    image_url_thumbnail TEXT NOT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_recipe_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

CREATE INDEX idx_recipe_user_id ON recipe(user_id);
CREATE INDEX idx_recipe_is_public ON recipe(is_public);
CREATE INDEX idx_recipe_label ON recipe(label);
CREATE INDEX idx_recipe_created_at ON recipe(created_at);

COMMENT ON TABLE recipe IS 'User-created recipes with ownership and visibility control';
COMMENT ON COLUMN recipe.is_public IS 'Whether recipe is visible to other users';

-- ============================================================================
-- STEP 4: CREATE REFRESH_TOKENS TABLE
-- ============================================================================
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    
    CONSTRAINT fk_refresh_tokens_user 
        FOREIGN KEY (user_id) 
        REFERENCES users(id) 
        ON DELETE CASCADE
);

-- CRITICAL indexes for security and performance
CREATE UNIQUE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_is_revoked ON refresh_tokens(is_revoked) WHERE is_revoked = FALSE;

COMMENT ON TABLE refresh_tokens IS 'Stores hashed refresh tokens with rotation and revocation support';

-- ============================================================================
-- STEP 5: INSERT TEST DATA
-- ============================================================================

-- Test Users (all passwords are the same: "TestPass123")
INSERT INTO users (id, email, username, password_hash) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'alice@test.com', 'Alice', '$2a$12$LQDHPwzzPWFERYtY3KfQaeFmIaM5b7YQ7GxJYqG.N7GnXvLqYG5Ai'),
    ('550e8400-e29b-41d4-a716-446655440002', 'bob@test.com', 'Bob', '$2a$12$LQDHPwzzPWFERYtY3KfQaeFmIaM5b7YQ7GxJYqG.N7GnXvLqYG5Ai'),
    ('550e8400-e29b-41d4-a716-446655440003', 'charlie@test.com', 'Charlie', '$2a$12$LQDHPwzzPWFERYtY3KfQaeFmIaM5b7YQ7GxJYqG.N7GnXvLqYG5Ai');

-- Alice's Recipes (public and private)
INSERT INTO recipe (uuid, user_id, title, label, description, prep_time_mins, recipe_url, image_url, image_url_thumbnail, is_public) VALUES
    (
        '3765597A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440001',
        'Jamaican Jerk Chicken',
        'Caribbean',
        'Fragrant, fiery hot, and smoky Caribbean classic with authentic spice blend.',
        60,
        'https://www.foodandwine.com/recipe/jamaican-jerk-chicken',
        'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg',
        'https://www.foodandwine.com/thmb/AbaDjGVLSIk8MP53z0ZVTPgv88M=/750x0/filters:no_upscale():max_bytes(150000):strip_icc():format(webp)/jamaican-jerk-chicken-FT-RECIPE0918-eabbd55da31f4fa9b74367ef47464351.jpg',
        TRUE
    ),
    (
        '1265597A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440001',
        'Andalusian Gazpacho',
        'Spanish',
        'Refreshing and healthy cold vegetable soup perfect for summer.',
        15,
        'https://www.javirecetas.com/gazpacho-receta-de-gazpacho-andaluz/',
        'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg',
        'https://www.javirecetas.com/wp-content/uploads/2009/07/gazpacho-1-600x900.jpg',
        TRUE
    ),
    (
        'AAAA597A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440001',
        'Alices Secret Pasta',
        'Italian',
        'My secret family recipe - not sharing with anyone!',
        30,
        'https://example.com/secret',
        'https://example.com/pasta.jpg',
        'https://example.com/pasta_thumb.jpg',
        FALSE -- Private recipe
    );

-- Bob's Recipes
INSERT INTO recipe (uuid, user_id, title, label, description, prep_time_mins, recipe_url, image_url, image_url_thumbnail, is_public) VALUES
    (
        '27655B7A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440002',
        'Herb Roasted French Rack of Elk',
        'NewAmerican',
        'Elegant wild game preparation with herb crust and red wine reduction.',
        45,
        'https://jhbuffalomeat.com/blogs/news/herb-roasted-french-rack-of-elk',
        'https://images.getrecipekit.com/20231115182037-plated-20with-20sauce.png?width=650&quality=90&',
        'https://images.getrecipekit.com/20231115182037-plated-20with-20sauce.png?width=650&quality=90&',
        TRUE
    ),
    (
        '2365597A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440002',
        'Black Rice (Arroz Negro)',
        'Spanish',
        'Seafood paella variant with squid ink for dramatic black color.',
        75,
        'https://www.javirecetas.com/arroz-negro/',
        'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg',
        'https://recetasdecocina.elmundo.es/wp-content/uploads/2017/08/arroz-negro-receta.jpg',
        TRUE
    );

-- Charlie's Recipes
INSERT INTO recipe (uuid, user_id, title, label, description, prep_time_mins, recipe_url, image_url, image_url_thumbnail, is_public) VALUES
    (
        '4565597A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440003',
        'Chorizo Lentils',
        'Spanish',
        'Hearty lentil stew with spicy chorizo sausage and vegetables.',
        45,
        'https://spanishsabores.com/chorizo-lentils/',
        'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg',
        'https://spanishsabores.com/wp-content/uploads/2024/02/Lentejas-con-Chorizo-Featured.jpg',
        TRUE
    ),
    (
        '5565597A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440003',
        'Coca Cola Pulled Pork',
        'NewAmerican',
        'Slow cooker pulled pork recipe cooked in Coca Cola. Could it be more American?',
        180,
        'https://www.recipetineats.com/coca-cola-pulled-pork/',
        'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg',
        'https://www.recipetineats.com/tachyon/2019/11/Close-up-of-pulled-pork-with-BBQ-Sauce.jpg',
        TRUE
    ),
    (
        '6565597A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440003',
        'Instant Pot Mac and Cheese',
        'NewAmerican',
        'Gloriously creamy homemade mac and cheese in under 30 minutes.',
        25,
        'https://www.pressurecookrecipe.com/pressure-cooker-mac-and-cheese/',
        'https://www.pressurecookrecipe.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg',
        'https://www.pressurecookrecipe.com/wp-content/uploads/2022/12/instant-pot-mac-and-cheese.jpg',
        TRUE
    ),
    (
        '7565597A-D982-42FC-9E42-41C788C69D5E',
        '550e8400-e29b-41d4-a716-446655440003',
        'Vegan Spinach Soup',
        'Vegan',
        'Creamy vegan spinach soup inspired by West African pepperpot.',
        45,
        'https://healthiersteps.com/vegan-spinach-soup/',
        'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg',
        'https://healthiersteps.com/wp-content/uploads/2019/01/gluten-free-vegan-spinach-soup.jpg',
        TRUE
    );

-- ============================================================================
-- STEP 6: VERIFICATION
-- ============================================================================

\echo ''
\echo '================================'
\echo 'DATABASE SCHEMA CREATED!'
\echo '================================'
\echo ''

-- Show summary
\echo 'TABLES:'
SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;

\echo ''
\echo 'INDEXES:'
SELECT 
    tablename,
    indexname
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

\echo ''
\echo 'RECORD COUNTS:'
SELECT 'users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'recipes', COUNT(*) FROM recipe
UNION ALL
SELECT 'refresh_tokens', COUNT(*) FROM refresh_tokens;

\echo ''
\echo '================================'
\echo 'TEST USERS (all passwords: "TestPass123"):'
\echo '================================'
SELECT email, username FROM users ORDER BY email;

\echo ''
\echo '================================'
\echo 'TEST RECIPES:'
\echo '================================'
SELECT 
    r.title,
    u.username as owner,
    r.label,
    CASE WHEN r.is_public THEN 'Public' ELSE 'Private' END as visibility,
    r.prep_time_mins as mins
FROM recipe r
JOIN users u ON r.user_id = u.id
ORDER BY u.username, r.title;

\echo ''
\echo '================================'
\echo 'READY TO USE!'
\echo '================================'
\echo 'Login with any test user:'
\echo '  Email: alice@test.com'
\echo '  Password: TestPass123'
\echo ''
\echo 'Or create your own:'
\echo '  POST /auth/register'
\echo '================================'
