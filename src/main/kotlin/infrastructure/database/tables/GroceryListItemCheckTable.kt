package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * One row per grocery-list item checkbox state, keyed by (meal plan, opaque client-derived item
 * key) rather than a surrogate id — there's nothing else to key it by, since the item itself only
 * exists as a string on the client. [checked] is an explicit boolean rather than a tombstone: an
 * uncheck is an ordinary LWW update, not a delete, so a toggle/untoggle cycle never accumulates
 * `deleted_at` rows. Visibility mirrors [MealPlanTable.household_id] — a grocery item is only ever
 * as shared as the plan it belongs to.
 */
object GroceryListItemCheckTable : Table("grocery_list_item_checks") {
    val meal_plan_id = reference("meal_plan_id", MealPlanTable, onDelete = ReferenceOption.CASCADE)
    val item_key = text("item_key")
    val checked = bool("checked")
    val checked_by = optReference("checked_by", UserTable, onDelete = ReferenceOption.SET_NULL)
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at").clientDefault { Clock.System.now() }.index()

    override val primaryKey = PrimaryKey(meal_plan_id, item_key)
}
