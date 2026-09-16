package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Membership rows are never deleted, only marked `status = 'REMOVED'` — they double as the audit
 * trail and as the tombstone source [com.tenmilelabs.infrastructure.database.repositoryImpl
 * .PostgresSyncRepository] reads from to tell a removed member's client which household-scoped
 * rows to drop locally (see [server_removed_at]).
 *
 * A user belongs to **at most one ACTIVE household at a time**. Exposed's `.uniqueIndex()` can't
 * express a filtered (partial) index, and a plain unique index on `user_id` would wrongly forbid
 * ever rejoining a household after leaving one — so this constraint is hand-written DDL, added by
 * [com.tenmilelabs.infrastructure.database.createHouseholdConstraintsIfMissing], in the same
 * category as the `recipes.search_vector` index `DatabaseInit.kt` already documents. It is the
 * real enforcement; [com.tenmilelabs.domain.service.HouseholdService]'s own pre-check is only the
 * fast path — see that class for how the two cooperate under a race.
 */
object HouseholdMemberTable : UUIDTable("household_members", "id") {
    val household_id = reference("household_id", HouseholdTable, onDelete = ReferenceOption.CASCADE).index()
    val user_id = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val role = text("role") // "OWNER" | "MEMBER"
    val status = text("status") // "ACTIVE" | "REMOVED"
    val joined_at = timestamp("joined_at").clientDefault { Clock.System.now() }
    val removed_at = timestamp("removed_at").nullable()

    /**
     * Set to the same instant as [removed_at] when a membership is removed, but kept as its own
     * column so a later correction to [removed_at] (e.g. an admin backfill) never disturbs a
     * tombstone cursor a client may already have consumed.
     */
    val server_removed_at = timestamp("server_removed_at").nullable()
}
