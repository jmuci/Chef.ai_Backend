package com.tenmilelabs.infrastructure.database

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
// Import all table objects
import com.tenmilelabs.infrastructure.database.tables.AllergenTable
import com.tenmilelabs.infrastructure.database.tables.BookmarkedRecipeTable
import com.tenmilelabs.infrastructure.database.tables.ImageBlobTable
import com.tenmilelabs.infrastructure.database.tables.IngredientTable
import com.tenmilelabs.infrastructure.database.tables.LabelTable
import com.tenmilelabs.infrastructure.database.tables.MealPlanDayTable
import com.tenmilelabs.infrastructure.database.tables.MealPlanTable
import com.tenmilelabs.infrastructure.database.tables.RecipeIngredientTable
import com.tenmilelabs.infrastructure.database.tables.RecipeLabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeStepTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTagTable
import com.tenmilelabs.infrastructure.database.tables.RefreshTokenTable
import com.tenmilelabs.infrastructure.database.tables.SourceClassificationTable
import com.tenmilelabs.infrastructure.database.tables.TagTable
import com.tenmilelabs.infrastructure.database.tables.UserPreferencesTable
import com.tenmilelabs.infrastructure.database.tables.UserTable

/**
 * Initializes the database and creates all tables if they do not exist.
 * Call this once at application startup (after you configure and connect Exposed's Database).
 *
 * Uses `createMissingTablesAndColumns` rather than `create` so that a column added to an
 * existing table (e.g. `recipes.image_blob_id`) is picked up on an already-provisioned
 * database, not just a fresh one. There is no separate migration runner in this project.
 */
fun initDatabaseAndSchema() {
    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            UserTable,
            AllergenTable,
            IngredientTable,
            LabelTable,
            RecipeIngredientTable,
            RecipeLabelTable,
            RecipeStepTable,
            RecipeTable,
            RecipeTagTable,
            RefreshTokenTable,
            SourceClassificationTable,
            TagTable,
            BookmarkedRecipeTable,
            MealPlanTable,
            MealPlanDayTable,
            ImageBlobTable,
            UserPreferencesTable
        )

        createRecipeSearchIndexIfMissing()
    }
}

/**
 * Full-text search support for `GET /api/v1/recipes/search` — a generated `tsvector` column plus a
 * GIN index over it, applied by hand because neither is expressible on an Exposed `Table` object
 * (Exposed 0.56 has no tsvector column type, and `SchemaUtils` cannot create generated columns or
 * GIN indexes).
 *
 * Deliberately **not** declared on [RecipeTable] — see the comment there. Both statements are
 * idempotent (`IF NOT EXISTS`), so this is safe to run on every boot. Mirrored by hand in
 * `sql/create_tables.sql` (fresh installs) and `sql/add_recipe_search.sql` (already-deployed
 * databases) — there is no migration runner in this project, so all three copies must be kept in
 * sync manually.
 *
 * The two-argument `to_tsvector(regconfig, text)` form is mandatory: verified against a live
 * Postgres 16 (`pg_proc.provolatile`) that it is the only overload that is IMMUTABLE, which
 * `GENERATED ALWAYS AS ... STORED` requires. The single-argument form is only STABLE (it depends on
 * the session's `default_text_search_config`) and Postgres rejects it here.
 */
private fun Transaction.createRecipeSearchIndexIfMissing() {
    exec(
        """
        ALTER TABLE recipes ADD COLUMN IF NOT EXISTS search_vector tsvector
          GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(description, '')), 'C')
          ) STORED
        """.trimIndent()
    )
    exec(
        "CREATE INDEX IF NOT EXISTS idx_recipes_search_vector ON recipes USING GIN (search_vector)"
    )
}

