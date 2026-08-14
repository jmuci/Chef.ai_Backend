package com.tenmilelabs.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class ImageUploadResponse(
    val imageBlobId: String,
    val updatedAt: Long
)

/** Stable machine-readable error codes the client is expected to branch on — see §3a/§5. */
@Serializable
data class ImageErrorResponse(
    val error: String
)
