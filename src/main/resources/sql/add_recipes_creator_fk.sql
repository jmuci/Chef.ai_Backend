-- Add FK from recipes.creator_id -> users.uuid with cascade delete.
-- Safe to run multiple times.

BEGIN;

-- Collect orphan recipe IDs first so child rows can be deleted safely.
CREATE TEMP TABLE orphan_recipe_ids ON COMMIT DROP AS
SELECT r.uuid
FROM recipes r
LEFT JOIN users u ON u.uuid = r.creator_id
WHERE u.uuid IS NULL;

-- Remove dependent rows in case child FKs are not cascading.
DELETE FROM recipe_steps rs
USING orphan_recipe_ids o
WHERE rs.recipe_id = o.uuid;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'recipe_ingredients' AND column_name = 'recipe_id'
    ) THEN
        EXECUTE 'DELETE FROM recipe_ingredients ri USING orphan_recipe_ids o WHERE ri.recipe_id = o.uuid';
    ELSIF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'recipe_ingredients' AND column_name = 'recipeId'
    ) THEN
        EXECUTE 'DELETE FROM recipe_ingredients ri USING orphan_recipe_ids o WHERE ri."recipeId" = o.uuid';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'recipe_labels' AND column_name = 'recipe_id'
    ) THEN
        EXECUTE 'DELETE FROM recipe_labels rl USING orphan_recipe_ids o WHERE rl.recipe_id = o.uuid';
    ELSIF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'recipe_labels' AND column_name = 'recipeId'
    ) THEN
        EXECUTE 'DELETE FROM recipe_labels rl USING orphan_recipe_ids o WHERE rl."recipeId" = o.uuid';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'recipe_tags' AND column_name = 'recipe_id'
    ) THEN
        EXECUTE 'DELETE FROM recipe_tags rt USING orphan_recipe_ids o WHERE rt.recipe_id = o.uuid';
    ELSIF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'recipe_tags' AND column_name = 'recipeId'
    ) THEN
        EXECUTE 'DELETE FROM recipe_tags rt USING orphan_recipe_ids o WHERE rt."recipeId" = o.uuid';
    END IF;
END $$;

-- Remove orphan recipes so the FK can be added cleanly.
DELETE FROM recipes r
USING orphan_recipe_ids o
WHERE r.uuid = o.uuid;

ALTER TABLE recipes
DROP CONSTRAINT IF EXISTS fk_recipes_creator;

ALTER TABLE recipes
ADD CONSTRAINT fk_recipes_creator
FOREIGN KEY (creator_id)
REFERENCES users(uuid)
ON DELETE CASCADE
ON UPDATE NO ACTION;

CREATE INDEX IF NOT EXISTS idx_recipes_creator_id ON recipes(creator_id);

COMMIT;
