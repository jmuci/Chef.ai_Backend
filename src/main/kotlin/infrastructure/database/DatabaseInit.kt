package com.tenmilelabs.infrastructure.database

import org.jetbrains.exposed.sql.SchemaUtils
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
            ImageBlobTable
        )
    }
}

