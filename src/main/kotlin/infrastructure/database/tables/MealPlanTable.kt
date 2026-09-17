package com.tenmilelabs.infrastructure.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object MealPlanTable : UUIDTable("meal_plans", "id") {
    val user_id = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()

    /**
     * Null = personal (default). Non-null = shared with every active member of that household,
     * widening [com.tenmilelabs.domain.repository.SyncRepository.getMealPlanForMember]. Never
     * touched by sync — only [com.tenmilelabs.domain.service.HouseholdService]'s leave/remove/
     * dissolve paths null it back out, and only a brand-new push may set it in the first place.
     */
    val household_id = optReference("household_id", HouseholdTable, onDelete = ReferenceOption.SET_NULL).index()

    /**
     * The household this plan was shared with immediately before it was detached (owner left the
     * household), or null if it's never been detached. Kept alongside [household_detached_at] so a
     * still-ACTIVE member of that household — who isn't otherwise told anything, since
     * [household_id] going null just makes the row silently stop matching their own
     * `memberAccessClause` — can still be sent a synthetic removal tombstone for it on their next
     * pull. See [com.tenmilelabs.domain.repository.SyncRepository.findDeltaMealPlans].
     */
    val former_household_id = optReference("former_household_id", HouseholdTable, onDelete = ReferenceOption.SET_NULL).index()

    /** Set together with [former_household_id] when this plan is detached; null otherwise. */
    val household_detached_at = timestamp("household_detached_at").nullable()
    val name = text("name")
    val status = text("status")
    val preferences = text("preferences")
    val created_at = long("created_at")
    val updated_at = long("updated_at")
    val deleted_at = long("deleted_at").nullable()
    val server_updated_at = timestamp("server_updated_at").index()
}
