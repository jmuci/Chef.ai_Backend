package com.tenmilelabs.domain.model

import java.util.UUID

/**
 * Whether a recipe hero image can be re-derived from a third-party source, or exists only
 * because the user uploaded it themselves. Derived server-side from `recipes.image_url` at
 * upload time — never accepted from the client. See ADR-011 Stage 2.
 */
enum class ImageProvenance {
    USER,
    SCRAPED
}

data class ImageBlobRecord(
    val id: UUID,
    val userId: UUID,
    val contentHash: String,
    val provenance: ImageProvenance,
    val mimeType: String,
    val byteSize: Long,
    val storageKey: String,
    val createdAt: Long,
    val unreferencedSince: Long?
)

/**
 * The subset of a recipe row needed to authorize and resolve its hero image.
 */
data class RecipeImageContext(
    val recipeId: UUID,
    val ownerId: UUID,
    val privacy: String,
    val imageUrl: String,
    val imageBlobId: String?,
    val updatedAtMillis: Long
)
