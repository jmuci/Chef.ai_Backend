package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.infrastructure.database.dao.RecipeDAO
import com.tenmilelabs.infrastructure.database.mappers.daoToModel
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import com.tenmilelabs.infrastructure.database.tables.UserTable
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class PostgresRecipesRepository(private val log: Logger) : RecipesRepository {
    override suspend fun allRecipes(): List<Recipe> = suspendTransaction {
        log.info("PostgresRecipesRepository recipes fetch all recipes")
        RecipeDAO.find { RecipeTable.deleted_at.isNull() }.map {
            log.info("RecipeDAO.all() ->  it ${it.title}  recipe")
            daoToModel(it)
        }
    }

    override suspend fun recipesByUserId(userId: UUID): List<Recipe> = suspendTransaction {
        RecipeDAO
            .find { (RecipeTable.creator_id eq userId) and (RecipeTable.deleted_at.isNull()) }
            .map(::daoToModel)
    }

    override suspend fun publicRecipes(): List<Recipe> = suspendTransaction {
        RecipeDAO
            .find { (RecipeTable.privacy eq "PUBLIC") and (RecipeTable.deleted_at.isNull()) }
            .map(::daoToModel)
    }

    override suspend fun recipeByTitle(title: String): Recipe? = suspendTransaction {
        RecipeDAO
            .find { (RecipeTable.title eq title) and (RecipeTable.deleted_at.isNull()) }
            .limit(1)
            .map(::daoToModel)
            .firstOrNull()
    }

    override suspend fun recipeById(id: String): Recipe? = suspendTransaction {
        RecipeDAO
            .find { (RecipeTable.id eq UUID.fromString(id)) and (RecipeTable.deleted_at.isNull()) }
            .limit(1)
            .firstOrNull()
            ?.let(::daoToModel)
    }

    override suspend fun recipeByIdAndUserId(id: String, userId: UUID): Recipe? = suspendTransaction {
        RecipeDAO
            .find {
                (RecipeTable.id eq UUID.fromString(id)) and
                    (RecipeTable.creator_id eq userId) and
                    (RecipeTable.deleted_at.isNull())
            }
            .limit(1)
            .map(::daoToModel)
            .firstOrNull()
    }

    override suspend fun addRecipe(recipeRequest: CreateRecipeRequest, userId: UUID): String = suspendTransaction {
        val recipeDAO = RecipeDAO.new {
            title = recipeRequest.title
            description = recipeRequest.description
            imageUrl = recipeRequest.imageUrl
            imageUrlThumbnail = recipeRequest.imageUrlThumbnail
            prepTimeMinutes = recipeRequest.prepTimeMinutes
            cookTimeMinutes = recipeRequest.cookTimeMinutes
            servings = recipeRequest.servings
            creatorId = EntityID(userId, UserTable)
            recipeExternalUrl = recipeRequest.recipeExternalUrl
            privacy = recipeRequest.privacy
            updatedAt = System.currentTimeMillis()
            deletedAt = null
            serverUpdatedAt = Clock.System.now()
        }
        recipeDAO.id.toString()
    }

    override suspend fun removeRecipe(uuid: String, userId: UUID): Boolean = suspendTransaction {
        val now = Clock.System.now()
        val rowsUpdated = RecipeTable.update({
            (RecipeTable.id eq UUID.fromString(uuid)) and
                (RecipeTable.creator_id eq userId) and
                (RecipeTable.deleted_at.isNull())
        }) {
            it[deleted_at] = now.toEpochMilliseconds()
            it[server_updated_at] = now
        }
        rowsUpdated == 1
    }

    override suspend fun purgeSoftDeletedRecipes(olderThanMillis: Long, limit: Int): Int = suspendTransaction {
        if (limit <= 0) return@suspendTransaction 0

        val recipeIds = RecipeTable
            .selectAll()
            .where {
                (RecipeTable.deleted_at.isNotNull()) and
                    (RecipeTable.deleted_at lessEq olderThanMillis)
            }
            .orderBy(RecipeTable.deleted_at to SortOrder.ASC)
            .limit(limit)
            .map { it[RecipeTable.id] }

        if (recipeIds.isEmpty()) return@suspendTransaction 0

        recipeIds.sumOf { recipeId ->
            RecipeTable.deleteWhere { RecipeTable.id eq recipeId }
        }
    }
}
