package com.tenmilelabs.domain.repository

import java.util.UUID

data class RecipeSearchRow(
    val uuid: UUID,
    val title: String,
    val description: String,
    val imageUrl: String,
    val imageUrlThumbnail: String,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val servings: Int,
    val creatorId: UUID,
    val privacy: String,
    val updatedAt: Long,
)

data class RecipeSearchPage(
    val rows: List<RecipeSearchRow>,
    val hasMore: Boolean,
)

data class TagRef(val uuid: UUID, val displayName: String)
data class LabelRef(val uuid: UUID, val displayName: String)

data class RecipeTagsAndLabels(
    val tags: List<TagRef>,
    val labels: List<LabelRef>,
)

interface RecipeSearchRepository {
    /**
     * Full-text searches recipes by [tsQuery] — an already-sanitised Postgres tsquery string, see
     * [com.tenmilelabs.domain.service.RecipeSearchQueryBuilder], never raw user input — scoped to
     * [userId]'s own recipes plus every `PUBLIC` recipe, or to `PUBLIC` recipes alone when
     * [userId] is null (an anonymous caller), excluding soft-deleted rows. Matches on
     * title/description (via the generated `search_vector` column) OR a tag/label whose display
     * name matches. Ranked with tag/label hits first, then by `ts_rank`, with a stable `uuid`
     * tiebreak for deterministic paging.
     *
     * [limit]/[offset] are assumed already clamped by the caller (see `RecipeSearchService`).
     */
    suspend fun search(userId: UUID?, tsQuery: String, limit: Int, offset: Int): RecipeSearchPage

    /**
     * Loads tag/label refs for a batch of recipe ids in two queries total — not one query per
     * recipe — used to hydrate the rows [search] returns into the response DTO.
     */
    suspend fun loadTagsAndLabels(recipeIds: List<UUID>): Map<UUID, RecipeTagsAndLabels>
}
