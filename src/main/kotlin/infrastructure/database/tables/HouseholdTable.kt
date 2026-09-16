package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * A household is a group of users sharing meal plans and grocery lists (see ADR-014, added
 * alongside this table). [owner_id] is a denormalized mirror of whichever [HouseholdMemberTable]
 * row currently has `role = 'OWNER' AND status = 'ACTIVE'` — the membership row is authoritative;
 * every ownership-transfer or dissolution path must update both in the same transaction, which is
 * why [HouseholdService] owns all such transitions rather than leaving repository callers to keep
 * them in sync by hand.
 */
object HouseholdTable : UUIDTable("households", "id") {
    val name = text("name")
    val owner_id = reference("owner_id", UserTable, onDelete = ReferenceOption.RESTRICT)
    val created_at = timestamp("created_at").clientDefault { Clock.System.now() }
    val updated_at = timestamp("updated_at").clientDefault { Clock.System.now() }
    val deleted_at = timestamp("deleted_at").nullable()
}
