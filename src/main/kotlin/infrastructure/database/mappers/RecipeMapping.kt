package com.tenmilelabs.infrastructure.database.mappers

import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.infrastructure.database.dao.RecipeDAO
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID
import com.tenmilelabs.infrastructure.database.tables.RecipeIngredientTable
import com.tenmilelabs.infrastructure.database.tables.RecipeLabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTagTable
import org.jetbrains.exposed.sql.ResultRow

suspend fun <T> suspendTransaction(block: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = block)

fun daoToModel(dao: RecipeDAO): Recipe =
    Recipe(
        uuid = dao.id.toString(),
        title = dao.title,
        description = dao.description,
        imageUrl = dao.imageUrl,
        imageUrlThumbnail = dao.imageUrlThumbnail,
        prepTimeMinutes = dao.prepTimeMinutes,
        cookTimeMinutes = dao.cookTimeMinutes,
        servings = dao.servings,
        creatorId = dao.creatorId.toString(),
        recipeExternalUrl = dao.recipeExternalUrl,
        privacy = enumValueOf(dao.privacy),
        updatedAt = dao.updatedAt,
        deletedAt = dao.deletedAt,
        serverUpdatedAt = dao.serverUpdatedAt.toString()
    )

// --- Relationship data classes ---
data class RecipeIngredient(
    val recipeId: UUID,
    val ingredientId: UUID,
    val quantity: Double,
    val unit: String,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String,
    val serverUpdatedAt: String
)

fun ResultRow.toRecipeIngredient() = RecipeIngredient(
    recipeId = this[RecipeIngredientTable.recipeId].value,
    ingredientId = this[RecipeIngredientTable.ingredientId].value,
    quantity = this[RecipeIngredientTable.quantity],
    unit = this[RecipeIngredientTable.unit],
    updatedAt = this[RecipeIngredientTable.updatedAt],
    deletedAt = this[RecipeIngredientTable.deletedAt],
    syncState = this[RecipeIngredientTable.syncState],
    serverUpdatedAt = this[RecipeIngredientTable.serverUpdatedAt].toString()
)

// Data class for RecipeLabel (m2m)
data class RecipeLabel(
    val recipeId: UUID,
    val labelId: UUID,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String,
    val serverUpdatedAt: String
)

fun ResultRow.toRecipeLabel() = RecipeLabel(
    recipeId = this[RecipeLabelTable.recipeId].value,
    labelId = this[RecipeLabelTable.labelId].value,
    updatedAt = this[RecipeLabelTable.updatedAt],
    deletedAt = this[RecipeLabelTable.deletedAt],
    syncState = this[RecipeLabelTable.syncState],
    serverUpdatedAt = this[RecipeLabelTable.serverUpdatedAt].toString()
)

// Data class for RecipeTag (m2m)
data class RecipeTag(
    val recipeId: UUID,
    val tagId: UUID,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncState: String,
    val serverUpdatedAt: String
)

fun ResultRow.toRecipeTag() = RecipeTag(
    recipeId = this[RecipeTagTable.recipeId].value,
    tagId = this[RecipeTagTable.tagId].value,
    updatedAt = this[RecipeTagTable.updatedAt],
    deletedAt = this[RecipeTagTable.deletedAt],
    syncState = this[RecipeTagTable.syncState],
    serverUpdatedAt = this[RecipeTagTable.serverUpdatedAt].toString()
)
