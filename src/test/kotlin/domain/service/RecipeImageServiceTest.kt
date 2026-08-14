package domain.service

import com.tenmilelabs.domain.service.ImageBlobConfig
import com.tenmilelabs.domain.service.ImageClearResult
import com.tenmilelabs.domain.service.ImageServeResult
import com.tenmilelabs.domain.service.ImageUploadResult
import com.tenmilelabs.domain.service.RecipeImageService
import com.tenmilelabs.infrastructure.database.FakeImageBlobRepository
import com.tenmilelabs.infrastructure.storage.FakeBlobStore
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01, 0x02, 0x03)
private val JPEG_BYTES_2 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x99.toByte(), 0x88.toByte(), 0x77.toByte())
private val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val WEBP_BYTES = "RIFF????WEBP".toByteArray(Charsets.US_ASCII).also {
    // "????" stands in for the 4-byte little-endian file size — never inspected by the sniffer.
    it[4] = 0; it[5] = 0; it[6] = 0; it[7] = 0
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

class RecipeImageServiceTest {
    @Test
    fun uploadAcceptsAUserPhotoAndPointsTheRecipeAtIt() = withService { service, repo, store ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "", updatedAtMillis = 100L)
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)

        val success = assertIs<ImageUploadResult.Success>(result)
        assertEquals(hash, success.imageBlobId)
        assertTrue(store.contains("$userId/$hash"))
        assertEquals(hash, repo.findRecipeForImage(recipeId)?.imageBlobId)
    }

    @Test
    fun uploadReturnsHashMismatchWhenDeclaredHashIsWrong() = withService { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")

        val result = service.uploadImage(userId, recipeId, "image/jpeg", "not-the-real-hash", JPEG_BYTES)

        assertEquals(ImageUploadResult.HashMismatch, result)
    }

    @Test
    fun uploadRejectsAPngBodyDeclaredAsJpeg() = withService { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(PNG_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/jpeg", hash, PNG_BYTES)

        assertEquals(ImageUploadResult.UnsupportedMediaType, result)
    }

    @Test
    fun uploadAcceptsAWebpImage() = withService { service, repo, store ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(WEBP_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/webp", hash, WEBP_BYTES)

        val success = assertIs<ImageUploadResult.Success>(result)
        assertEquals(hash, success.imageBlobId)
        assertTrue(store.contains("$userId/$hash"))
    }

    @Test
    fun uploadRejectsAContentTypeOutsideTheAllowedThree() = withService { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/gif", hash, JPEG_BYTES)

        assertEquals(ImageUploadResult.UnsupportedMediaType, result)
    }

    @Test
    fun uploadRejectsAMissingDeclaredHash() = withService { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")

        val result = service.uploadImage(userId, recipeId, "image/jpeg", null, JPEG_BYTES)

        assertEquals(ImageUploadResult.HashMismatch, result)
    }

    @Test
    fun uploadHashComparisonIsCaseInsensitive() = withService { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/jpeg", hash.uppercase(), JPEG_BYTES)

        assertIs<ImageUploadResult.Success>(result)
    }

    @Test
    fun uploadRejectsABodyOverTheConfiguredCap() = withService(
        config = ImageBlobConfig(maxUploadBytes = 4)
    ) { service, repo, store ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)

        assertEquals(ImageUploadResult.PayloadTooLarge, result)
        assertTrue(store.listAllKeys().isEmpty())
    }

    @Test
    fun uploadReturnsNotFoundForAnotherUsersRecipe() = withService { service, repo, _ ->
        val owner = UUID.randomUUID()
        val stranger = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = owner, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(stranger, recipeId, "image/jpeg", hash, JPEG_BYTES)

        assertEquals(ImageUploadResult.RecipeNotFound, result)
    }

    @Test
    fun uploadReturnsNotFoundForAnUnknownRecipe() = withService { service, _, _ ->
        val userId = UUID.randomUUID()
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(userId, UUID.randomUUID(), "image/jpeg", hash, JPEG_BYTES)

        assertEquals(ImageUploadResult.RecipeNotFound, result)
    }

    @Test
    fun uploadOfAScrapedImageIsRejectedWhenTheKillSwitchIsOff() = withService(
        config = ImageBlobConfig(allowScrapedImageUpload = false)
    ) { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "https://example.com/photo.jpg")
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)

        assertEquals(ImageUploadResult.ScrapedUploadDisabled, result)
    }

    @Test
    fun uploadOfAUserPhotoStillWorksWhenScrapedUploadsAreDisabled() = withService(
        config = ImageBlobConfig(allowScrapedImageUpload = false)
    ) { service, repo, _ ->
        val userId = UUID.randomUUID()
        // blank imageUrl => USER provenance, unaffected by the scraped-upload kill switch
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)

        assertIs<ImageUploadResult.Success>(result)
    }

    @Test
    fun uploadRejectsWhenOverQuota() = withService(
        config = ImageBlobConfig(perUserQuotaBytes = JPEG_BYTES.size.toLong() - 1)
    ) { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)

        val result = service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)

        assertEquals(ImageUploadResult.QuotaExceeded, result)
    }

    @Test
    fun reUploadingIdenticalBytesIsANoOpThatDoesNotMoveUpdatedAt() = withService { service, repo, store ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "", updatedAtMillis = 100L)
        val hash = sha256Hex(JPEG_BYTES)

        val first = assertIs<ImageUploadResult.Success>(
            service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)
        )
        val second = assertIs<ImageUploadResult.Success>(
            service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)
        )

        assertEquals(first.updatedAtMillis, second.updatedAtMillis)
        assertEquals(1, store.listAllKeys().size)
    }

    @Test
    fun uploadingDifferentBytesRepointsAndDereferencesThePreviousBlob() = withService { service, repo, store ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val firstHash = sha256Hex(JPEG_BYTES)
        assertIs<ImageUploadResult.Success>(
            service.uploadImage(userId, recipeId, "image/jpeg", firstHash, JPEG_BYTES)
        )

        val secondHash = sha256Hex(JPEG_BYTES_2)
        val result = service.uploadImage(userId, recipeId, "image/jpeg", secondHash, JPEG_BYTES_2)

        val success = assertIs<ImageUploadResult.Success>(result)
        assertEquals(secondHash, success.imageBlobId)
        assertEquals(secondHash, repo.findRecipeForImage(recipeId)?.imageBlobId)
        // Nothing else points at the first blob anymore, so it's now a reclamation candidate.
        assertEquals(success.updatedAtMillis, repo.blobFor(userId, firstHash)?.unreferencedSince)
        assertTrue(store.contains("$userId/$firstHash"))
        assertTrue(store.contains("$userId/$secondHash"))
    }

    @Test
    fun uploadingTheSameBytesForAnotherRecipeReusesTheBlobWithoutRewritingIt() = withService { service, repo, store ->
        val userId = UUID.randomUUID()
        val firstRecipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val secondRecipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)
        assertIs<ImageUploadResult.Success>(
            service.uploadImage(userId, firstRecipeId, "image/jpeg", hash, JPEG_BYTES)
        )

        val result = service.uploadImage(userId, secondRecipeId, "image/jpeg", hash, JPEG_BYTES)

        val success = assertIs<ImageUploadResult.Success>(result)
        assertEquals(hash, success.imageBlobId)
        assertEquals(hash, repo.findRecipeForImage(secondRecipeId)?.imageBlobId)
        assertEquals(1, store.listAllKeys().size)
    }

    @Test
    fun serveReturnsNotFoundForAPrivateRecipeOwnedByAnotherUser() = withService { service, repo, _ ->
        val owner = UUID.randomUUID()
        val stranger = UUID.randomUUID()
        val (recipeId, _) = uploadJpeg(service, repo, owner, privacy = "PRIVATE")

        val result = service.serveImage(stranger, recipeId, null)

        assertEquals(ImageServeResult.NotFound, result)
    }

    @Test
    fun serveReturnsTheImageForAPublicRecipeRegardlessOfProvenance() = withService { service, repo, _ ->
        val owner = UUID.randomUUID()
        val stranger = UUID.randomUUID()
        // blank imageUrl => USER provenance
        val recipeId = repo.seedRecipe(ownerId = owner, privacy = "PUBLIC", imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)
        assertIs<ImageUploadResult.Success>(service.uploadImage(owner, recipeId, "image/jpeg", hash, JPEG_BYTES))

        val result = service.serveImage(stranger, recipeId, null)

        val found = assertIs<ImageServeResult.Found>(result)
        assertEquals(hash, found.contentHash)
    }

    @Test
    fun serveHidesScrapedImagesFromNonOwnersWhenTheKillSwitchIsOff() = withService(
        config = ImageBlobConfig(serveScrapedBlobsToNonOwners = false)
    ) { service, repo, _ ->
        val owner = UUID.randomUUID()
        val stranger = UUID.randomUUID()
        val recipeId = repo.seedRecipe(
            ownerId = owner,
            privacy = "PUBLIC",
            imageUrl = "https://example.com/photo.jpg" // SCRAPED provenance
        )
        val hash = sha256Hex(JPEG_BYTES)
        assertIs<ImageUploadResult.Success>(service.uploadImage(owner, recipeId, "image/jpeg", hash, JPEG_BYTES))

        val strangerResult = service.serveImage(stranger, recipeId, null)
        assertEquals(ImageServeResult.NotFound, strangerResult)

        val ownerResult = service.serveImage(owner, recipeId, null)
        assertIs<ImageServeResult.Found>(ownerResult)
    }

    @Test
    fun serveReturnsNotModifiedWhenIfNoneMatchMatchesTheCurrentHash() = withService { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)
        service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)

        val result = service.serveImage(userId, recipeId, "\"$hash\"")

        assertEquals(ImageServeResult.NotModified(hash), result)
    }

    @Test
    fun clearRemovesThePointerAndMarksTheBlobUnreferenced() = withService { service, repo, _ ->
        val userId = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = userId, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)
        service.uploadImage(userId, recipeId, "image/jpeg", hash, JPEG_BYTES)

        val result = service.clearImage(userId, recipeId)

        assertIs<ImageClearResult.Success>(result)
        assertNull(repo.findRecipeForImage(recipeId)?.imageBlobId)
        assertEquals(result.updatedAtMillis, repo.blobFor(userId, hash)?.unreferencedSince)
    }

    @Test
    fun clearReturnsNotFoundForAnotherUsersRecipe() = withService { service, repo, _ ->
        val owner = UUID.randomUUID()
        val stranger = UUID.randomUUID()
        val recipeId = repo.seedRecipe(ownerId = owner, imageUrl = "")

        val result = service.clearImage(stranger, recipeId)

        assertEquals(ImageClearResult.NotFound, result)
    }

    private suspend fun uploadJpeg(
        service: RecipeImageService,
        repo: FakeImageBlobRepository,
        ownerId: UUID,
        privacy: String
    ): Pair<UUID, String> {
        val recipeId = repo.seedRecipe(ownerId = ownerId, privacy = privacy, imageUrl = "")
        val hash = sha256Hex(JPEG_BYTES)
        assertIs<ImageUploadResult.Success>(service.uploadImage(ownerId, recipeId, "image/jpeg", hash, JPEG_BYTES))
        return recipeId to hash
    }

    private fun withService(
        config: ImageBlobConfig = ImageBlobConfig(),
        block: suspend (RecipeImageService, FakeImageBlobRepository, FakeBlobStore) -> Unit
    ) {
        val repo = FakeImageBlobRepository()
        val store = FakeBlobStore()
        val service = RecipeImageService(repo, store, config, KtorSimpleLogger("test"))
        runBlocking { block(service, repo, store) }
    }
}
