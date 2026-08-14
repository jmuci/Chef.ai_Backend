package com.tenmilelabs.presentation.routes

import com.tenmilelabs.application.dto.ErrorResponse
import com.tenmilelabs.application.dto.ImageErrorResponse
import com.tenmilelabs.application.dto.ImageUploadResponse
import com.tenmilelabs.domain.service.ImageBlobConfig
import com.tenmilelabs.domain.service.ImageClearResult
import com.tenmilelabs.domain.service.ImageServeResult
import com.tenmilelabs.domain.service.ImageUploadResult
import com.tenmilelabs.domain.service.RecipeImageService
import com.tenmilelabs.infrastructure.auth.userId
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.util.UUID

val RECIPE_IMAGE_UPLOAD_RATE_LIMIT_NAME = RateLimitName("recipe-image-upload")

fun Route.recipeImageRoutes(recipeImageService: RecipeImageService, config: ImageBlobConfig) {
    route("/recipes/{recipeId}/image") {
        rateLimit(RECIPE_IMAGE_UPLOAD_RATE_LIMIT_NAME) {
            put {
                val userId = parseUuidOrNull(call.userId) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
                    return@put
                }
                val recipeId = call.parameters["recipeId"]?.let(::parseUuidOrNull) ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("recipeId is not a valid UUID"))
                    return@put
                }

                // Enforced while streaming so an oversized body is never buffered in full —
                // see docs/recipe-image-architecture.md § Upload Flow, validation order.
                val bytes = readBodyCapped(call.receiveChannel(), config.maxUploadBytes)
                if (bytes == null) {
                    call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Image exceeds maximum upload size"))
                    return@put
                }

                val declaredHash = call.request.header("X-Content-SHA256")
                val contentType = call.request.contentType().toString()

                when (
                    val result = recipeImageService.uploadImage(userId, recipeId, contentType, declaredHash, bytes)
                ) {
                    is ImageUploadResult.Success ->
                        call.respond(
                            HttpStatusCode.OK,
                            ImageUploadResponse(result.imageBlobId, result.updatedAtMillis)
                        )
                    ImageUploadResult.RecipeNotFound ->
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("Recipe not found"))
                    ImageUploadResult.UnsupportedMediaType ->
                        call.respond(HttpStatusCode.UnsupportedMediaType, ErrorResponse("Unsupported image type"))
                    ImageUploadResult.PayloadTooLarge ->
                        call.respond(
                            HttpStatusCode.PayloadTooLarge,
                            ErrorResponse("Image exceeds maximum upload size")
                        )
                    ImageUploadResult.HashMismatch ->
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorResponse("X-Content-SHA256 does not match the uploaded bytes")
                        )
                    ImageUploadResult.ScrapedUploadDisabled ->
                        call.respond(HttpStatusCode.Forbidden, ImageErrorResponse("SCRAPED_UPLOAD_DISABLED"))
                    ImageUploadResult.QuotaExceeded ->
                        call.respond(HttpStatusCode.Forbidden, ImageErrorResponse("QUOTA_EXCEEDED"))
                }
            }
        }

        get {
            val userId = parseUuidOrNull(call.userId) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
                return@get
            }
            val recipeId = call.parameters["recipeId"]?.let(::parseUuidOrNull) ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("recipeId is not a valid UUID"))
                return@get
            }
            val ifNoneMatch = call.request.header(HttpHeaders.IfNoneMatch)

            when (val result = recipeImageService.serveImage(userId, recipeId, ifNoneMatch)) {
                is ImageServeResult.Found -> {
                    call.response.header(HttpHeaders.ETag, "\"${result.contentHash}\"")
                    call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
                    result.stream.use { stream ->
                        call.respondOutputStream(
                            contentType = ContentType.parse(result.mimeType),
                            status = HttpStatusCode.OK
                        ) {
                            stream.copyTo(this)
                        }
                    }
                }
                is ImageServeResult.NotModified -> {
                    call.response.header(HttpHeaders.ETag, "\"${result.contentHash}\"")
                    call.respond(HttpStatusCode.NotModified)
                }
                ImageServeResult.NotFound -> call.respond(HttpStatusCode.NotFound)
            }
        }

        delete {
            val userId = parseUuidOrNull(call.userId) ?: run {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not authenticated"))
                return@delete
            }
            val recipeId = call.parameters["recipeId"]?.let(::parseUuidOrNull) ?: run {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("recipeId is not a valid UUID"))
                return@delete
            }

            when (recipeImageService.clearImage(userId, recipeId)) {
                is ImageClearResult.Success -> call.respond(HttpStatusCode.NoContent)
                ImageClearResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Recipe not found"))
            }
        }
    }
}

private fun parseUuidOrNull(value: String?): UUID? =
    try {
        value?.let(UUID::fromString)
    } catch (_: IllegalArgumentException) {
        null
    }

/**
 * Reads [channel] into memory, aborting as soon as more than [capBytes] have arrived so an
 * oversized body is never buffered in full. Returns null on overflow.
 */
private suspend fun readBodyCapped(channel: ByteReadChannel, capBytes: Long): ByteArray? {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_CHUNK_SIZE)
    var total = 0L
    while (true) {
        val read = channel.readAvailable(chunk)
        if (read == -1) break
        total += read
        if (total > capBytes) return null
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

private const val DEFAULT_CHUNK_SIZE = 8192
