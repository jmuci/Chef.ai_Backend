package com.tenmilelabs.domain.service

import com.tenmilelabs.application.dto.RecipeSearchResponse
import com.tenmilelabs.application.dto.RecipeSearchResultDto
import com.tenmilelabs.application.dto.SidecarLabelDto
import com.tenmilelabs.application.dto.SidecarTagDto
import com.tenmilelabs.domain.repository.RecipeSearchRepository
import com.tenmilelabs.domain.repository.RecipeTagsAndLabels
import java.util.UUID

class RecipeSearchService(private val recipeSearchRepository: RecipeSearchRepository) {

    /**
     * [requestedLimit]/[requestedOffset] come straight from query params and are clamped here —
     * the repository trusts its callers completely, so this is the one place that enforces it.
     *
     * A null [userId] is an anonymous caller, not an error: results are scoped to `PUBLIC`
     * recipes only. See `Routing.kt` for why the route admits them.
     */
    suspend fun search(
        userId: UUID?,
        rawQuery: String,
        requestedLimit: Int?,
        requestedOffset: Int?,
    ): RecipeSearchResponse {
        val limit = (requestedLimit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val offset = (requestedOffset ?: 0).coerceIn(0, MAX_OFFSET)

        // A query that sanitises down to nothing (all punctuation, blank, ...) must short-circuit
        // here — to_tsquery('english', '') raises a Postgres syntax error, it does not just match
        // nothing.
        val tsQuery = RecipeSearchQueryBuilder.build(rawQuery)
            ?: return RecipeSearchResponse(query = rawQuery, results = emptyList(), hasMore = false)

        val page = recipeSearchRepository.search(userId, tsQuery, limit, offset)
        val tagsAndLabelsByRecipe = recipeSearchRepository.loadTagsAndLabels(page.rows.map { it.uuid })

        val results = page.rows.map { row ->
            val tagsAndLabels = tagsAndLabelsByRecipe[row.uuid] ?: RecipeTagsAndLabels(emptyList(), emptyList())
            RecipeSearchResultDto(
                uuid = row.uuid.toString(),
                title = row.title,
                description = row.description,
                imageUrl = row.imageUrl,
                imageUrlThumbnail = row.imageUrlThumbnail,
                prepTimeMinutes = row.prepTimeMinutes,
                cookTimeMinutes = row.cookTimeMinutes,
                servings = row.servings,
                creatorId = row.creatorId.toString(),
                privacy = row.privacy,
                updatedAt = row.updatedAt,
                tags = tagsAndLabels.tags.map { SidecarTagDto(it.uuid.toString(), it.displayName) },
                labels = tagsAndLabels.labels.map { SidecarLabelDto(it.uuid.toString(), it.displayName) },
            )
        }

        return RecipeSearchResponse(query = rawQuery, results = results, hasMore = page.hasMore)
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
        const val MAX_OFFSET = 500
    }
}
