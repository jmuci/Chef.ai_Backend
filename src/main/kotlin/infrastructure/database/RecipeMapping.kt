package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

suspend fun <T> suspendTransaction(block: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, statement = block)

fun daoToModel(dao: RecipeDAO): Recipe =
    // TODO handle IllegalArgumentException
    Recipe(
        dao.id.toString(),
        dao.title,
        Label.valueOf(dao.label),
        dao.description,
        dao.prepTimeMins,
        dao.recipeUrl,
        dao.imageUrl,
        dao.imageUrlThumbnail,
        dao.createdAt.toString(),
        dao.userId.toString(),
        dao.isPublic
    )
