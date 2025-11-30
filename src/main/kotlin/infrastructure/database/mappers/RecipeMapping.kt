package com.tenmilelabs.infrastructure.database.mappers

import com.tenmilelabs.domain.model.Recipe
import com.tenmilelabs.infrastructure.database.dao.RecipeDAO
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

suspend fun <T> suspendTransaction(block: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = block)

fun daoToModel(dao: RecipeDAO): Recipe =
    Recipe(
        uuid = dao.id.toString(),
        title = dao.title,
        description = dao.description,
        imageUrl = dao.imageUrl,
        imageUrlThumbnail = dao.imageUrlThumbnail,
        prepTimeMinutes = dao.prepTimeMinutes,
        cookTimeMinutes = dao.cookTimeMinutes,
        servings = dao.servings,
        creatorId = dao.creatorId.toString(),
        recipeExternalUrl = dao.recipeExternalUrl,
        privacy = enumValueOf(dao.privacy),
        updatedAt = dao.updatedAt,
        deletedAt = dao.deletedAt,
        syncState = dao.syncState,
        serverUpdatedAt = dao.serverUpdatedAt.toString()
    )
