package com.tenmilelabs.infrastructure.database.integration

import com.tenmilelabs.domain.service.RecipeSearchQueryBuilder
import com.tenmilelabs.infrastructure.database.initDatabaseAndSchema
import com.tenmilelabs.infrastructure.database.repositoryImpl.PostgresRecipeSearchRepository
import com.tenmilelabs.infrastructure.database.tables.LabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeLabelTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.RecipeTagTable
import com.tenmilelabs.infrastructure.database.tables.TagTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This is the tier that actually matters for search: fakes exercise no Postgres FTS at all, so
 * this is where a Kotlin-side bug in the raw SQL (`explicitStatementType`, placeholder order,
 * a join that bypasses the visibility predicate) would actually surface.
 */
@Tag("db-integration")
class PostgresRecipeSearchRepositoryIntegrationTest {

    @BeforeEach
    fun resetSchema() {
        Database.connect(
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/chefai_db",
            user = System.getenv("DB_USER") ?: "postgres",
            password = System.getenv("DB_PASSWORD") ?: "password"
        )

        transaction {
            exec("DROP SCHEMA IF EXISTS public CASCADE")
            exec("CREATE SCHEMA public")
        }

        initDatabaseAndSchema()
    }

    @Test
    fun `schema init is idempotent and the generated column plus GIN index exist`() {
        // resetSchema()'s @BeforeEach already ran initDatabaseAndSchema() once against a bare
        // schema; running it again here proves the ALTER TABLE ADD COLUMN IF NOT EXISTS / CREATE
        // INDEX IF NOT EXISTS in DatabaseInit.kt survive being applied a second time.
        initDatabaseAndSchema()

        transaction {
            val columnExists = exec(
                "SELECT 1 FROM information_schema.columns " +
                    "WHERE table_name = 'recipes' AND column_name = 'search_vector'"
            ) { rs -> rs.next() }
            val indexExists = exec(
                "SELECT 1 FROM pg_indexes WHERE tablename = 'recipes' AND indexname = 'idx_recipes_search_vector'"
            ) { rs -> rs.next() }
            assertEquals(true, columnExists)
            assertEquals(true, indexExists)
        }
    }

    @Test
    fun `generated search_vector populates on insert and recomputes on update`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        val recipeId = UUID.randomUUID()
        transaction {
            insertUser(userId)
            insertRecipe(recipeId, title = "Original Casserole", creatorId = userId)
        }

        val beforeUpdate = repo.search(userId, tsQueryFor("Casserole"), 20, 0)
        assertEquals(listOf(recipeId), beforeUpdate.rows.map { it.uuid })

        transaction {
            RecipeTable.update({ RecipeTable.id eq recipeId }) {
                it[title] = "Renamed Frittata"
            }
        }

        val afterUpdateOldTerm = repo.search(userId, tsQueryFor("Casserole"), 20, 0)
        assertTrue(afterUpdateOldTerm.rows.isEmpty())

        val afterUpdateNewTerm = repo.search(userId, tsQueryFor("Frittata"), 20, 0)
        assertEquals(listOf(recipeId), afterUpdateNewTerm.rows.map { it.uuid })
    }

    @Test
    fun `prefix matching and stemming both work through the live index`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        val chickenId = UUID.randomUUID()
        val tomatoId = UUID.randomUUID()
        transaction {
            insertUser(userId)
            insertRecipe(chickenId, title = "Chicken Tikka Masala", creatorId = userId)
            insertRecipe(tomatoId, title = "Fresh Tomato Salad", creatorId = userId)
        }

        val prefixResult = repo.search(userId, tsQueryFor("chic"), 20, 0)
        assertEquals(listOf(chickenId), prefixResult.rows.map { it.uuid })

        val stemmedResult = repo.search(userId, tsQueryFor("tomatoes"), 20, 0)
        assertEquals(listOf(tomatoId), stemmedResult.rows.map { it.uuid })
    }

    @Test
    fun `tag-only and label-only matches are found with zero title or description overlap`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        val tagRecipeId = UUID.randomUUID()
        val labelRecipeId = UUID.randomUUID()
        val tagId = UUID.randomUUID()
        val labelId = UUID.randomUUID()
        transaction {
            insertUser(userId)
            insertRecipe(tagRecipeId, title = "Weeknight Bowl", description = "Quick dinner", creatorId = userId)
            insertRecipe(labelRecipeId, title = "Sunday Roast", description = "Slow cooked", creatorId = userId)
            insertTag(tagId, "Seafood")
            insertLabel(labelId, "Ketogenic")
            linkTag(tagRecipeId, tagId)
            linkLabel(labelRecipeId, labelId)
        }

        val tagResult = repo.search(userId, tsQueryFor("seafood"), 20, 0)
        assertEquals(listOf(tagRecipeId), tagResult.rows.map { it.uuid })

        val labelResult = repo.search(userId, tsQueryFor("ketogenic"), 20, 0)
        assertEquals(listOf(labelRecipeId), labelResult.rows.map { it.uuid })
    }

    @Test
    fun `a stranger's PRIVATE recipe never appears, even via a tag match, but the owner sees it`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val ownerId = UUID.randomUUID()
        val strangerId = UUID.randomUUID()
        val privateRecipeId = UUID.randomUUID()
        val tagId = UUID.randomUUID()
        transaction {
            insertUser(ownerId, "owner")
            insertUser(strangerId, "stranger")
            insertRecipe(privateRecipeId, title = "Family Secret Chili", creatorId = ownerId, privacy = "PRIVATE")
            insertTag(tagId, "Zzyxqorbnak")
            linkTag(privateRecipeId, tagId)
        }

        // The easy way to get this wrong: join the tag CTE in a way that bypasses the visibility
        // predicate entirely. Cover both the tag path and the plain title path.
        val strangerSeesByTag = repo.search(strangerId, tsQueryFor("Zzyxqorbnak"), 20, 0)
        assertTrue(strangerSeesByTag.rows.isEmpty())

        val strangerSeesByTitle = repo.search(strangerId, tsQueryFor("Family Secret Chili"), 20, 0)
        assertTrue(strangerSeesByTitle.rows.isEmpty())

        val ownerSees = repo.search(ownerId, tsQueryFor("Zzyxqorbnak"), 20, 0)
        assertEquals(listOf(privateRecipeId), ownerSees.rows.map { it.uuid })
    }

    @Test
    fun `soft-deleted recipes and soft-deleted tag links are excluded`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        val deletedRecipeId = UUID.randomUUID()
        val activeRecipeId = UUID.randomUUID()
        val deletedLinkTagId = UUID.randomUUID()
        transaction {
            insertUser(userId)
            insertRecipe(deletedRecipeId, title = "Ghost Kitchen Bowl", creatorId = userId, deletedAt = 5_000L)
            insertRecipe(activeRecipeId, title = "Unrelated Dish", creatorId = userId)
            insertTag(deletedLinkTagId, "Vanishing")
            // The recipe is active but the *link* is soft-deleted — must not count as a tag hit.
            linkTag(activeRecipeId, deletedLinkTagId, deletedAt = 5_000L)
        }

        val deletedRecipeResult = repo.search(userId, tsQueryFor("Ghost Kitchen"), 20, 0)
        assertTrue(deletedRecipeResult.rows.isEmpty())

        val deletedLinkResult = repo.search(userId, tsQueryFor("Vanishing"), 20, 0)
        assertTrue(deletedLinkResult.rows.isEmpty())
    }

    @Test
    fun `a title match ranks above a description-only match`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        val titleHitId = UUID.randomUUID()
        val descriptionOnlyHitId = UUID.randomUUID()
        transaction {
            insertUser(userId)
            insertRecipe(
                descriptionOnlyHitId,
                title = "Weeknight Bowl",
                description = "A jackfruit-based dinner",
                creatorId = userId
            )
            insertRecipe(titleHitId, title = "Jackfruit Tacos", description = "Tacos", creatorId = userId)
        }

        val result = repo.search(userId, tsQueryFor("jackfruit"), 20, 0)
        assertEquals(listOf(titleHitId, descriptionOnlyHitId), result.rows.map { it.uuid })
    }

    @Test
    fun `ordering is deterministic across repeated identical calls`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        transaction {
            insertUser(userId)
            repeat(5) { insertRecipe(UUID.randomUUID(), title = "Broccoli Stir Fry $it", creatorId = userId) }
        }

        val tsQuery = tsQueryFor("broccoli")
        val firstCall = repo.search(userId, tsQuery, 20, 0).rows.map { it.uuid }
        val secondCall = repo.search(userId, tsQuery, 20, 0).rows.map { it.uuid }
        assertEquals(firstCall, secondCall)
    }

    @Test
    fun `limit and offset paginate through all matches without duplicates or gaps`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        val allIds = List(5) { UUID.randomUUID() }
        transaction {
            insertUser(userId)
            allIds.forEachIndexed { index, id -> insertRecipe(id, title = "Lentil Curry $index", creatorId = userId) }
        }

        val tsQuery = tsQueryFor("lentil")
        val page1 = repo.search(userId, tsQuery, 2, 0)
        val page2 = repo.search(userId, tsQuery, 2, 2)
        val page3 = repo.search(userId, tsQuery, 2, 4)

        assertTrue(page1.hasMore)
        assertTrue(page2.hasMore)
        assertTrue(!page3.hasMore)

        val seenIds = (page1.rows + page2.rows + page3.rows).map { it.uuid }
        assertEquals(allIds.toSet(), seenIds.toSet())
        assertEquals(seenIds.size, seenIds.toSet().size)
    }

    @Test
    fun `adversarial and metacharacter-bearing queries return cleanly without a Postgres error`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        transaction {
            insertUser(userId)
            insertRecipe(UUID.randomUUID(), title = "Mac and Cheese Bake", creatorId = userId)
        }

        val queries = listOf(
            "' OR 1=1--",
            "mac & cheese",
            "chef's salad",
            "recipe; DROP TABLE recipes;--",
            "a:b",
        )
        queries.forEach { raw ->
            val tsQuery = RecipeSearchQueryBuilder.build(raw) ?: return@forEach
            // The assertion is that this doesn't throw — a to_tsquery syntax error surfaces as an
            // exception here, not as an empty result.
            repo.search(userId, tsQuery, 20, 0)
        }
    }

    @Test
    fun `loadTagsAndLabels hydrates tags and labels for a batch of recipes in two queries`() = runBlocking {
        val repo = PostgresRecipeSearchRepository()
        val userId = UUID.randomUUID()
        val recipeAId = UUID.randomUUID()
        val recipeBId = UUID.randomUUID()
        val tagId = UUID.randomUUID()
        val labelId = UUID.randomUUID()
        transaction {
            insertUser(userId)
            insertRecipe(recipeAId, title = "Recipe A", creatorId = userId)
            insertRecipe(recipeBId, title = "Recipe B", creatorId = userId)
            insertTag(tagId, "Quick")
            insertLabel(labelId, "Vegan")
            linkTag(recipeAId, tagId)
            linkLabel(recipeAId, labelId)
        }

        val result = repo.loadTagsAndLabels(listOf(recipeAId, recipeBId))

        assertEquals(setOf(recipeAId, recipeBId), result.keys)
        assertEquals(listOf("Quick"), result.getValue(recipeAId).tags.map { it.displayName })
        assertEquals(listOf("Vegan"), result.getValue(recipeAId).labels.map { it.displayName })
        assertTrue(result.getValue(recipeBId).tags.isEmpty())
        assertTrue(result.getValue(recipeBId).labels.isEmpty())
    }

    private fun tsQueryFor(raw: String): String =
        requireNotNull(RecipeSearchQueryBuilder.build(raw)) { "'$raw' sanitised to nothing" }

    private fun insertUser(id: UUID, suffix: String = id.toString()) {
        UserTable.insert {
            it[UserTable.id] = EntityID(id, UserTable)
            it[user_name] = "user-$suffix"
            it[email] = "user-$suffix@example.com"
            it[display_name] = "User $suffix"
            it[avatar_url] = ""
            it[password_hash] = "hash"
        }
    }

    private fun insertRecipe(
        id: UUID,
        title: String,
        creatorId: UUID,
        description: String = "",
        privacy: String = "PUBLIC",
        deletedAt: Long? = null,
        updatedAt: Long = 1_000L,
    ) {
        RecipeTable.insert {
            it[RecipeTable.id] = EntityID(id, RecipeTable)
            it[RecipeTable.title] = title
            it[RecipeTable.description] = description
            it[image_url] = "https://example.com/$id.jpg"
            it[image_url_thumbnail] = "https://example.com/$id-thumb.jpg"
            it[prep_time_minutes] = 10
            it[cook_time_minutes] = 10
            it[servings] = 2
            it[creator_id] = EntityID(creatorId, UserTable)
            it[RecipeTable.privacy] = privacy
            it[updated_at] = updatedAt
            it[deleted_at] = deletedAt
            it[server_updated_at] = Instant.fromEpochMilliseconds(updatedAt)
        }
    }

    private fun insertTag(id: UUID, displayName: String) {
        TagTable.insert {
            it[TagTable.id] = EntityID(id, TagTable)
            it[display_name] = displayName
            it[updated_at] = 1_000L
            it[deleted_at] = null
            it[server_updated_at] = Instant.fromEpochMilliseconds(1_000L)
        }
    }

    private fun insertLabel(id: UUID, displayName: String) {
        LabelTable.insert {
            it[LabelTable.id] = EntityID(id, LabelTable)
            it[display_name] = displayName
            it[updated_at] = 1_000L
            it[deleted_at] = null
            it[server_updated_at] = Instant.fromEpochMilliseconds(1_000L)
        }
    }

    private fun linkTag(recipe: UUID, tag: UUID, deletedAt: Long? = null) {
        RecipeTagTable.insert {
            it[RecipeTagTable.recipeId] = EntityID(recipe, RecipeTable)
            it[RecipeTagTable.tagId] = EntityID(tag, TagTable)
            it[RecipeTagTable.updatedAt] = 1_000L
            it[RecipeTagTable.deletedAt] = deletedAt
            it[RecipeTagTable.serverUpdatedAt] = Instant.fromEpochMilliseconds(1_000L)
        }
    }

    private fun linkLabel(recipe: UUID, label: UUID) {
        RecipeLabelTable.insert {
            it[RecipeLabelTable.recipeId] = EntityID(recipe, RecipeTable)
            it[RecipeLabelTable.labelId] = EntityID(label, LabelTable)
            it[RecipeLabelTable.updatedAt] = 1_000L
            it[RecipeLabelTable.deletedAt] = null
            it[RecipeLabelTable.serverUpdatedAt] = Instant.fromEpochMilliseconds(1_000L)
        }
    }
}
