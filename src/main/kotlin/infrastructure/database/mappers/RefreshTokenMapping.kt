package com.tenmilelabs.infrastructure.database.mappers

import com.tenmilelabs.domain.model.RefreshToken
import com.tenmilelabs.infrastructure.database.dao.RefreshTokenDAO

fun daoToRefreshToken(dao: RefreshTokenDAO): RefreshToken = RefreshToken(
    id = dao.id.toString(),
    userId = dao.userId,
    tokenHash = dao.tokenHash,
    expiresAt = dao.expiresAt.toString(),
    createdAt = dao.createdAt.toString(),
    isRevoked = dao.isRevoked,
    revokedAt = dao.revokedAt?.toString()
)
