package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UserPreferencesTable : Table("user_preferences") {
    val user_id = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val preferences = text("preferences")
    val updated_at = timestamp("updated_at")

    override val primaryKey = PrimaryKey(user_id)
}
