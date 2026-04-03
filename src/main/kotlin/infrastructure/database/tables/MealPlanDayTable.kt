package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object MealPlanDayTable : UUIDTable("meal_plan_days", "id") {
    val meal_plan_id = reference("meal_plan_id", MealPlanTable, onDelete = ReferenceOption.CASCADE).index()
    val day_index = integer("day_index")
    val dinner_recipe_id = optReference("dinner_recipe_id", RecipeTable, onDelete = ReferenceOption.SET_NULL)
    val lunch_recipe_id = optReference("lunch_recipe_id", RecipeTable, onDelete = ReferenceOption.SET_NULL)

    init {
        uniqueIndex(meal_plan_id, day_index)
    }
}
