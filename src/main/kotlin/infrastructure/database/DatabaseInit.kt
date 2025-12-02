package com.tenmilelabs.infrastructure.database

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Database
// Import all table objects
import com.tenmilelabs.infrastructure.database.tables.UserTable
import com.tenmilelabs.infrastructure.database.tables.AllergenTable
import com.tenmilelabs.infrastructure.database.tables.IngredientTable
import com.tenmilelabs.infrastructure.database.tables.LabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeIngredientTable
import com.tenmilelabs.infrastructure.database.tables.RecipeLabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeStepTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTagTable
import com.tenmilelabs.infrastructure.database.tables.RefreshTokenTable
import com.tenmilelabs.infrastructure.database.tables.SourceClassificationTable
import com.tenmilelabs.infrastructure.database.tables.TagTable

/**
 * Initializes the database and creates all tables if they do not exist.
 * Call this once at application startup (after you configure and connect Exposed's Database).
 */
fun initDatabaseAndSchema() {
    transaction {
        SchemaUtils.create(
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
            TagTable
        )
    }
}

