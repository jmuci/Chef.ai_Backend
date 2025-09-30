package com.tenmilelabs.infrastructure.database

import com.tenmilelabs.domain.model.RefreshToken

fun daoToRefreshToken(dao: RefreshTokenDAO): RefreshToken = RefreshToken(
    id = dao.id.toString(),
    userId = dao.userId,
    tokenHash = dao.tokenHash,
    expiresAt = dao.expiresAt.toString(),
    createdAt = dao.createdAt.toString(),
    isRevoked = dao.isRevoked,
    revokedAt = dao.revokedAt?.toString()
)
