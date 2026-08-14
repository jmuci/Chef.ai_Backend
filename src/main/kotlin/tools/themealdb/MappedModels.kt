package com.tenmilelabs.tools.themealdb

import java.util.UUID

/** Snapshot of the existing catalog the mapper resolves names against before minting new rows. */
data class Catalog(
    val ingredientIdsByNormalizedName: Map<String, UUID> = emptyMap(),
    val tagIdsByNormalizedName: Map<String, UUID> = emptyMap(),
    val labelIdsByNormalizedName: Map<String, UUID> = emptyMap()
) {
    companion object {
        val EMPTY = Catalog()
    }
}

data class MappedIngredient(val id: UUID, val displayName: String, val isNew: Boolean)
data class MappedTag(val id: UUID, val displayName: String, val isNew: Boolean)
data class MappedLabel(val id: UUID, val displayName: String, val isNew: Boolean)

data class MappedRecipeIngredient(val ingredientId: UUID, val quantity: Double, val unit: String)
data class MappedStep(val id: UUID, val orderIndex: Int, val instruction: String)

data class MappedRecipe(
    val id: UUID,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: UUID,
    val recipeExternalUrl: String?,
    val updatedAtMillis: Long,
    val steps: List<MappedStep>,
    val ingredients: List<MappedRecipeIngredient>,
    val tagIds: List<UUID>,
    val labelIds: List<UUID>
)

data class MappingResult(
    val recipes: List<MappedRecipe>,
    val newIngredients: List<MappedIngredient>,
    val newTags: List<MappedTag>,
    val newLabels: List<MappedLabel>
)
