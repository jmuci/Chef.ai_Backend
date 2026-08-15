package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecipeTable : UUIDTable("recipes", "uuid") {
    val title = text("title")
    val description = text("description")
    val image_url = text("image_url")
    val image_url_thumbnail = text("image_url_thumbnail")
    val prep_time_minutes = integer("prep_time_minutes")
    val cook_time_minutes = integer("cook_time_minutes")
    val servings = integer("servings")
    val creator_id = reference("creator_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val recipe_external_url = text("recipe_external_url").nullable()
    val privacy = text("privacy")
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at").index()
    /** Content hash of the blob in [ImageBlobTable], not a foreign key — see ADR-011 Stage 2. */
    val image_blob_id = text("image_blob_id").nullable()

    // NOTE: the `recipes` table also has a `search_vector tsvector GENERATED ALWAYS AS (...)
    // STORED` column plus a GIN index over it, used by GET /api/v1/recipes/search. It is
    // deliberately NOT declared here: Exposed 0.56 has no tsvector column type, the column is
    // server-generated (never written by application code), and `SchemaUtils
    // .createMissingTablesAndColumns` only ever creates columns/indices it doesn't recognise on
    // a Table object — it never drops or alters ones that are present in the DB but absent here,
    // so leaving it undeclared is safe. See `infrastructure/database/DatabaseInit.kt`
    // (createRecipeSearchIndexIfMissing) for where it's actually created.
}
