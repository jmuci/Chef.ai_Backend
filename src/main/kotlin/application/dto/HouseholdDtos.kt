package com.tenmilelabs.application.dto

import com.tenmilelabs.domain.model.Household
import com.tenmilelabs.domain.model.HouseholdInvite
import com.tenmilelabs.domain.model.HouseholdInvitePreview
import com.tenmilelabs.domain.model.HouseholdMembership
import kotlinx.serialization.Serializable

@Serializable
data class CreateHouseholdRequest(val name: String)

@Serializable
data class RenameHouseholdRequest(val name: String)

@Serializable
data class MemberResponse(
    val userId: String,
    val displayName: String,
    val avatarUrl: String,
    val role: String,
    val joinedAt: Long,
)

@Serializable
data class HouseholdResponse(
    val id: String,
    val name: String,
    val ownerId: String,
    val members: List<MemberResponse>,
)

@Serializable
data class CreateInviteRequest(
    val inviteeEmail: String? = null,
    val singleUse: Boolean = true,
    val maxUses: Int? = null,
    val expiresInHours: Long? = null,
)

@Serializable
data class CreateInviteResponse(
    val token: String,
    val url: String,
    val expiresAt: Long,
    val singleUse: Boolean,
    val maxUses: Int?,
)

@Serializable
data class InviteSummaryResponse(
    val id: String,
    val householdId: String,
    val inviteeEmail: String?,
    val singleUse: Boolean,
    val maxUses: Int?,
    val useCount: Int,
    val expiresAt: Long,
    val createdAt: Long,
)

@Serializable
data class InvitePreviewResponse(
    val householdName: String,
    val inviterDisplayName: String,
)

@Serializable
data class JoinHouseholdRequest(val token: String)

fun Household.toResponse(): HouseholdResponse = HouseholdResponse(
    id = id.toString(),
    name = name,
    ownerId = ownerId.toString(),
    members = members.map { it.toResponse() },
)

fun HouseholdMembership.toResponse(): MemberResponse = MemberResponse(
    userId = userId.toString(),
    displayName = displayName,
    avatarUrl = avatarUrl,
    role = role.name,
    joinedAt = joinedAt.toEpochMilliseconds(),
)

fun HouseholdInvite.toSummaryResponse(): InviteSummaryResponse = InviteSummaryResponse(
    id = id.toString(),
    householdId = householdId.toString(),
    inviteeEmail = inviteeEmail,
    singleUse = singleUse,
    maxUses = maxUses,
    useCount = useCount,
    expiresAt = expiresAt.toEpochMilliseconds(),
    createdAt = createdAt.toEpochMilliseconds(),
)

fun HouseholdInvitePreview.toResponse(): InvitePreviewResponse = InvitePreviewResponse(
    householdName = householdName,
    inviterDisplayName = inviterDisplayName,
)
