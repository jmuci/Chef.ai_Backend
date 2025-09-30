package com.tenmilelabs.infrastructure.database.dao

import com.tenmilelabs.infrastructure.database.tables.RefreshTokenTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class RefreshTokenDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RefreshTokenDAO>(RefreshTokenTable)

    var userId by RefreshTokenTable.userId
    var tokenHash by RefreshTokenTable.tokenHash
    var expiresAt by RefreshTokenTable.expiresAt
    var createdAt by RefreshTokenTable.createdAt
    var isRevoked by RefreshTokenTable.isRevoked
    var revokedAt by RefreshTokenTable.revokedAt
}
