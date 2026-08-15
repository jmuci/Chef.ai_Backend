package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.domain.repository.LabelRef
import com.tenmilelabs.domain.repository.RecipeSearchPage
import com.tenmilelabs.domain.repository.RecipeSearchRepository
import com.tenmilelabs.domain.repository.RecipeSearchRow
import com.tenmilelabs.domain.repository.RecipeTagsAndLabels
import com.tenmilelabs.domain.repository.TagRef
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.LabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeLabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTagTable
import com.tenmilelabs.infrastructure.database.tables.TagTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.StatementType
import java.util.UUID

// Placeholder order matters — it's positional, not named — and must match the args list in
// search() below exactly: tsQuery x4 (tag match, label match, rank, title/description match),
// then userId, then limit, then offset.
private val SEARCH_SQL = """
    WITH tag_hits AS (
        SELECT rt.recipe_id AS recipe_id
        FROM recipe_tags rt
        INNER JOIN tags t ON t.uuid = rt.tag_id
        WHERE rt.deleted_at IS NULL
          AND t.deleted_at IS NULL
          AND to_tsvector('english', t.display_name) @@ to_tsquery('english', ?::text)
        UNION
        SELECT rl.recipe_id AS recipe_id
        FROM recipe_labels rl
        INNER JOIN labels l ON l.uuid = rl.label_id
        WHERE rl.deleted_at IS NULL
          AND l.deleted_at IS NULL
          AND to_tsvector('english', l.display_name) @@ to_tsquery('english', ?::text)
    )
    SELECT
        r.uuid,
        r.title,
        r.description,
        r.image_url,
        r.image_url_thumbnail,
        r.prep_time_minutes,
        r.cook_time_minutes,
        r.servings,
        r.creator_id,
        r.privacy,
        r.updated_at,
        (th.recipe_id IS NOT NULL) AS tag_or_label_hit,
        ts_rank(r.search_vector, to_tsquery('english', ?::text)) AS rank
    FROM recipes r
    LEFT JOIN tag_hits th ON th.recipe_id = r.uuid
    WHERE r.deleted_at IS NULL
      AND (r.search_vector @@ to_tsquery('english', ?::text) OR th.recipe_id IS NOT NULL)
      AND (r.creator_id = ?::uuid OR r.privacy = 'PUBLIC')
    ORDER BY tag_or_label_hit DESC, rank DESC, r.uuid ASC
    LIMIT ? OFFSET ?
""".trimIndent()

/**
 * Raw SQL, not the Exposed DSL — deliberate exception to the usual house style. Exposed 0.56 has
 * no way to express `@@`/`to_tsquery`/`ts_rank`, and `search_vector` isn't a declared column (see
 * `RecipeTable`) for Exposed to build a DSL expression against in the first place.
 */
class PostgresRecipeSearchRepository : RecipeSearchRepository {

    override suspend fun search(userId: UUID, tsQuery: String, limit: Int, offset: Int): RecipeSearchPage =
        suspendTransaction {
            // Request one extra row so `hasMore` doesn't need a second COUNT(*) query.
            val fetchLimit = limit + 1

            val rows = exec(
                stmt = SEARCH_SQL,
                args = listOf(
                    TextColumnType() to tsQuery,
                    TextColumnType() to tsQuery,
                    TextColumnType() to tsQuery,
                    TextColumnType() to tsQuery,
                    UUIDColumnType() to userId,
                    IntegerColumnType() to fetchLimit,
                    IntegerColumnType() to offset,
                ),
                // Mandatory: SEARCH_SQL starts with WITH, which exec()'s keyword-based inference
                // doesn't recognise — it falls through to OTHER (grouped with DDL) and is routed
                // through executeUpdate() instead of executeQuery(), which PgJDBC rejects for a
                // statement that returns a ResultSet. Confirmed by decompiling exposed-core-0.56.0.
                explicitStatementType = StatementType.SELECT,
            ) { rs ->
                // The transform must fully drain the ResultSet before returning — exec() closes it
                // the instant the lambda returns, so a lazy Sequence would throw on first access.
                buildList {
                    while (rs.next()) {
                        add(
                            RecipeSearchRow(
                                uuid = rs.getObject("uuid", UUID::class.java),
                                title = rs.getString("title"),
                                description = rs.getString("description"),
                                imageUrl = rs.getString("image_url"),
                                imageUrlThumbnail = rs.getString("image_url_thumbnail"),
                                prepTimeMinutes = rs.getInt("prep_time_minutes"),
                                cookTimeMinutes = rs.getInt("cook_time_minutes"),
                                servings = rs.getInt("servings"),
                                creatorId = rs.getObject("creator_id", UUID::class.java),
                                privacy = rs.getString("privacy"),
                                updatedAt = rs.getLong("updated_at"),
                            )
                        )
                    }
                }
            }.orEmpty()

            RecipeSearchPage(rows = rows.take(limit), hasMore = rows.size > limit)
        }

    override suspend fun loadTagsAndLabels(recipeIds: List<UUID>): Map<UUID, RecipeTagsAndLabels> =
        suspendTransaction {
            if (recipeIds.isEmpty()) return@suspendTransaction emptyMap()

            val recipeEntityIds = recipeIds.map { EntityID(it, RecipeTable) }

            val tagCrossRefs = RecipeTagTable
                .selectAll()
                .where { (RecipeTagTable.recipeId inList recipeEntityIds) and RecipeTagTable.deletedAt.isNull() }
                .map { it[RecipeTagTable.recipeId].value to it[RecipeTagTable.tagId].value }

            val tagIds = tagCrossRefs.map { it.second }.distinct().map { EntityID(it, TagTable) }
            val tagNamesById = if (tagIds.isEmpty()) {
                emptyMap()
            } else {
                TagTable
                    .selectAll()
                    .where { TagTable.id inList tagIds }
                    .associate { it[TagTable.id].value to it[TagTable.display_name] }
            }

            val tagsByRecipe = tagCrossRefs
                .mapNotNull { (recipeId, tagId) -> tagNamesById[tagId]?.let { recipeId to TagRef(tagId, it) } }
                .groupBy({ it.first }, { it.second })

            val labelCrossRefs = RecipeLabelTable
                .selectAll()
                .where { (RecipeLabelTable.recipeId inList recipeEntityIds) and RecipeLabelTable.deletedAt.isNull() }
                .map { it[RecipeLabelTable.recipeId].value to it[RecipeLabelTable.labelId].value }

            val labelIds = labelCrossRefs.map { it.second }.distinct().map { EntityID(it, LabelTable) }
            val labelNamesById = if (labelIds.isEmpty()) {
                emptyMap()
            } else {
                LabelTable
                    .selectAll()
                    .where { LabelTable.id inList labelIds }
                    .associate { it[LabelTable.id].value to it[LabelTable.display_name] }
            }

            val labelsByRecipe = labelCrossRefs
                .mapNotNull { (recipeId, labelId) -> labelNamesById[labelId]?.let { recipeId to LabelRef(labelId, it) } }
                .groupBy({ it.first }, { it.second })

            recipeIds.associateWith { recipeId ->
                RecipeTagsAndLabels(
                    tags = tagsByRecipe[recipeId].orEmpty(),
                    labels = labelsByRecipe[recipeId].orEmpty(),
                )
            }
        }
}
