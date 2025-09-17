package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.Label
import com.tenmilelabs.domain.model.Recipe
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

object RecipeTable : UUIDTable("recipe", "uuid") {
    val title = varchar("title", 200)
    val label = varchar("label", 20) //TODO there should be a table for labels
    val description = text("description")

    val prepTimeMins = integer("prep_time_mins")
    val recipeUrl = text("recipe_url")
    val imageUrl = text("image_url")
    val imageUrlThumbnail = text("image_url_thumbnail")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

}

class RecipeDAO(uuid: EntityID<UUID>) : UUIDEntity(uuid) {
    companion object : UUIDEntityClass<RecipeDAO>(RecipeTable)

    var title by RecipeTable.title
    var label by RecipeTable.label
    var description by RecipeTable.description

    var prepTimeMins by RecipeTable.prepTimeMins
    var recipeUrl by RecipeTable.recipeUrl
    var imageUrl by RecipeTable.imageUrl
    var imageUrlThumbnail by RecipeTable.imageUrlThumbnail
    var createdAt by RecipeTable.createdAt
}

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
    )