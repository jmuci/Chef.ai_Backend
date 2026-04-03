package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MealPlanTable : UUIDTable("meal_plans", "id") {
    val user_id = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val name = text("name")
    val status = text("status")
    val preferences = text("preferences")
    val created_at = long("created_at")
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at").index()
}
