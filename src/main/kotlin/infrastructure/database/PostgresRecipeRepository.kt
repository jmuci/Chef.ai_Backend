package com.tenmilelabs.infrastructure.database


import com.tenmilelabs.application.dto.CreateRecipeRequest
import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import io.ktor.util.logging.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

    override suspend fun recipesByLabel(label: Label): List<Recipe> = suspendTransaction {
        RecipeDAO
            .find { (RecipeTable.label eq label.toString()) }
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

    override suspend fun addRecipe(recipeRequest: CreateRecipeRequest): String = suspendTransaction {
        val recipeDAO = RecipeDAO.new {
            title = recipeRequest.title
            description = recipeRequest.description
            label = recipeRequest.label
            prepTimeMins = Integer.valueOf(recipeRequest.prepTimeMins)
            imageUrl = recipeRequest.imageUrl
            recipeUrl = recipeRequest.imageUrl
            imageUrlThumbnail = recipeRequest.imageUrlThumbnail
        }
        //daoToModel(RecipeDAO.findById(recipeDAO.id)  ?: error("Inserted recipe with id=${recipeDAO.id} not found"))
        recipeDAO.id.toString()
    }

    override suspend fun removeRecipe(uuid: String): Boolean = suspendTransaction {
        val rowsDeleted = RecipeTable.deleteWhere {
            RecipeTable.id eq UUID.fromString(uuid)
        }
        rowsDeleted == 1
    }
}