package com.tenmilelabs.infrastructure.database.tables

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * [token_hash] is `sha256(rawToken)` — the raw token is returned to the caller exactly once, on
 * creation, and is never persisted anywhere. Same primitive family as [RefreshTokenTable], via
 * [com.tenmilelabs.domain.util.TokenHasher].
 *
 * [invitee_user_id] is resolved to a concrete account at creation time for an email-addressed
 * invite (not left as a bare email to match later), so authorization at accept time is an id
 * comparison, never a string comparison — see the backend prompt's tradeoff note. [invitee_email]
 * is kept alongside purely as a denormalized display/audit field.
 */
object HouseholdInviteTable : UUIDTable("household_invites", "id") {
    val household_id = reference("household_id", HouseholdTable, onDelete = ReferenceOption.CASCADE).index()
    val created_by = reference("created_by", UserTable, onDelete = ReferenceOption.CASCADE)
    val token_hash = varchar("token_hash", 255).uniqueIndex()
    val invitee_user_id = optReference("invitee_user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val invitee_email = text("invitee_email").nullable()
    val single_use = bool("single_use").default(true)
    val max_uses = integer("max_uses").nullable()
    val use_count = integer("use_count").default(0)
    val expires_at = timestamp("expires_at")
    val accepted_by = optReference("accepted_by", UserTable, onDelete = ReferenceOption.SET_NULL)
    val accepted_at = timestamp("accepted_at").nullable()
    val revoked_at = timestamp("revoked_at").nullable()
    val created_at = timestamp("created_at").clientDefault { Clock.System.now() }
}
