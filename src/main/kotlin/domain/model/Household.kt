package com.tenmilelabs.domain.model

import kotlinx.datetime.Instant
import java.util.UUID

enum class HouseholdRole { OWNER, MEMBER }

enum class HouseholdMemberStatus { ACTIVE, REMOVED }

data class Household(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val members: List<HouseholdMembership>,
)

data class HouseholdMembership(
    val householdId: UUID,
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String,
    val role: HouseholdRole,
    val status: HouseholdMemberStatus,
    val joinedAt: Instant,
    val removedAt: Instant?,
    val serverRemovedAt: Instant?,
)

data class HouseholdInvite(
    val id: UUID,
    val householdId: UUID,
    val createdBy: UUID,
    val tokenHash: String,
    val inviteeUserId: UUID?,
    val inviteeEmail: String?,
    val singleUse: Boolean,
    val maxUses: Int?,
    val useCount: Int,
    val expiresAt: Instant,
    val acceptedBy: UUID?,
    val acceptedAt: Instant?,
    val revokedAt: Instant?,
    val createdAt: Instant,
) {
    /** True when the invite may still be used to join: not revoked, not expired, budget left. */
    fun isUsable(now: Instant): Boolean {
        if (revokedAt != null) return false
        if (now >= expiresAt) return false
        if (singleUse) return acceptedAt == null
        val budget = maxUses ?: return true
        return useCount < budget
    }
}

data class NewHouseholdInvite(
    val householdId: UUID,
    val createdBy: UUID,
    val tokenHash: String,
    val inviteeUserId: UUID?,
    val inviteeEmail: String?,
    val singleUse: Boolean,
    val maxUses: Int?,
    val expiresAt: Instant,
)
