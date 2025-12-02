package com.tenmilelabs.infrastructure.database.repositoryImpl

import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.domain.repository.RecipesRepository
import com.tenmilelabs.infrastructure.database.dao.RecipeDAO
import com.tenmilelabs.infrastructure.database.mappers.daoToModel
import com.tenmilelabs.infrastructure.database.mappers.suspendTransaction
import com.tenmilelabs.infrastructure.database.tables.RecipeTable
import io.ktor.util.logging.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import java.util.*

class PostgresRecipesRepository(private val log: Logger) : RecipesRepository {
    override suspend fun allRecipes(): List<Recipe> = suspendTransaction {
        log.info("PostgresRecipesRepository recipes fetch all recipes")
        RecipeDAO.all().map {
            log.info("RecipeDAO.all() ->  it ${it.title}  recipe")
            daoToModel(it)
        }
    }

    override suspend fun recipesByUserId(userId: UUID): List<Recipe> = suspendTransaction {
        RecipeDAO
            .find { RecipeTable.creator_id eq userId }
            .map(::daoToModel)
    }

    override suspend fun publicRecipes(): List<Recipe> = suspendTransaction {
        RecipeDAO
            .find { RecipeTable.privacy eq "public" }
            .map(::daoToModel)
    }

    override suspend fun recipeByTitle(title: String): Recipe? = suspendTransaction {
        RecipeDAO
            .find { (RecipeTable.title eq title) }
            .limit(1)
            .map(::daoToModel)
            .firstOrNull()
    }

    override suspend fun recipeById(id: String): Recipe? = suspendTransaction {
        RecipeDAO
            .findById(UUID.fromString(id))
            ?.let(::daoToModel)
    }

    override suspend fun recipeByIdAndUserId(id: String, userId: UUID): Recipe? = suspendTransaction {
        RecipeDAO
            .find { (RecipeTable.id eq UUID.fromString(id)) and (RecipeTable.creator_id eq userId) }
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
            creatorId = userId
            recipeExternalUrl = recipeRequest.recipeExternalUrl
            privacy = recipeRequest.privacy
            updatedAt = System.currentTimeMillis()
            deletedAt = null
            syncState = "created"
            serverUpdatedAt = Clock.System.now()
        }
        recipeDAO.id.toString()
    }

    override suspend fun removeRecipe(uuid: String, userId: UUID): Boolean = suspendTransaction {
        val rowsDeleted = RecipeTable.deleteWhere {
            (RecipeTable.id eq UUID.fromString(uuid)) and (RecipeTable.creator_id eq userId)
        }
        rowsDeleted == 1
    }
}