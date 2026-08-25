package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.repository.RecipeSearchPage
import com.tenmilelabs.domain.repository.RecipeSearchRepository
import com.tenmilelabs.domain.repository.RecipeTagsAndLabels
import java.util.UUID

/**
 * Records the args it was called with (so route tests can assert the service clamped
 * limit/offset before they reached here) and returns whatever canned page the test configured —
 * it does no real matching, that's PostgresRecipeSearchRepositoryIntegrationTest's job.
 */
class FakeRecipeSearchRepository : RecipeSearchRepository {
    var pageToReturn: RecipeSearchPage = RecipeSearchPage(rows = emptyList(), hasMore = false)
    var tagsAndLabelsToReturn: Map<UUID, RecipeTagsAndLabels> = emptyMap()

    /** `lastUserId` alone can't tell "called with null" (anonymous) from "never called". */
    var searchCallCount: Int = 0
        private set
    var lastUserId: UUID? = null
        private set
    var lastTsQuery: String? = null
        private set
    var lastLimit: Int? = null
        private set
    var lastOffset: Int? = null
        private set

    override suspend fun search(userId: UUID?, tsQuery: String, limit: Int, offset: Int): RecipeSearchPage {
        searchCallCount++
        lastUserId = userId
        lastTsQuery = tsQuery
        lastLimit = limit
        lastOffset = offset
        return pageToReturn
    }

    override suspend fun loadTagsAndLabels(recipeIds: List<UUID>): Map<UUID, RecipeTagsAndLabels> =
        tagsAndLabelsToReturn
}
