package domain.service

import com.tenmilelabs.domain.model.ImageProvenance
import com.tenmilelabs.domain.service.ImageBlobConfig
import com.tenmilelabs.domain.service.ImageBlobReclamationService
import com.tenmilelabs.infrastructure.database.FakeImageBlobRepository
import com.tenmilelabs.infrastructure.storage.FakeBlobStore
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageBlobReclamationServiceTest {
    @Test
    fun sweepHardDeletesABlobUnreferencedPastTheRetentionWindow() = runBlocking {
        val repo = FakeImageBlobRepository()
        val store = FakeBlobStore()
        val config = ImageBlobConfig(unreferencedRetentionDays = 30)
        val service = ImageBlobReclamationService(repo, store, config, KtorSimpleLogger("test"))

        val userId = UUID.randomUUID()
        val hash = "expired-hash"
        val storageKey = "$userId/$hash"
        store.put(storageKey, byteArrayOf(1, 2, 3), "image/jpeg")
        val blob = repo.insertBlob(userId, hash, ImageProvenance.USER, "image/jpeg", 3L, storageKey, createdAt = 0L)
        val farInThePast = -40L * 24 * 60 * 60 * 1000
        repo.refreshUnreferencedState(userId, hash, nowMillis = farInThePast)

        val deleted = service.sweepOnce()

        assertEquals(1, deleted)
        assertNull(repo.blobFor(userId, hash))
        assertTrue(!store.contains(storageKey))
        assertTrue(blob.contentHash == hash) // sanity: we swept the blob we seeded
    }

    @Test
    fun sweepLeavesABlobThatIsStillWithinTheRetentionWindow() = runBlocking {
        val repo = FakeImageBlobRepository()
        val store = FakeBlobStore()
        val config = ImageBlobConfig(unreferencedRetentionDays = 30)
        val service = ImageBlobReclamationService(repo, store, config, KtorSimpleLogger("test"))

        val userId = UUID.randomUUID()
        val hash = "recent-hash"
        val storageKey = "$userId/$hash"
        store.put(storageKey, byteArrayOf(1, 2, 3), "image/jpeg")
        repo.insertBlob(userId, hash, ImageProvenance.USER, "image/jpeg", 3L, storageKey, createdAt = 0L)
        repo.refreshUnreferencedState(userId, hash, nowMillis = System.currentTimeMillis())

        val deleted = service.sweepOnce()

        assertEquals(0, deleted)
        assertTrue(store.contains(storageKey))
    }

    @Test
    fun sweepRespectsTheBatchSizeWhenMultipleBlobsAreExpired() = runBlocking {
        val repo = FakeImageBlobRepository()
        val store = FakeBlobStore()
        val config = ImageBlobConfig(unreferencedRetentionDays = 30)
        val service = ImageBlobReclamationService(repo, store, config, KtorSimpleLogger("test"))

        val userId = UUID.randomUUID()
        val farInThePast = -40L * 24 * 60 * 60 * 1000
        repeat(3) { i ->
            val hash = "expired-hash-$i"
            val storageKey = "$userId/$hash"
            store.put(storageKey, byteArrayOf(1, 2, 3), "image/jpeg")
            repo.insertBlob(userId, hash, ImageProvenance.USER, "image/jpeg", 3L, storageKey, createdAt = 0L)
            repo.refreshUnreferencedState(userId, hash, nowMillis = farInThePast)
        }

        val deleted = service.sweepOnce(batchSize = 2)

        assertEquals(2, deleted)
        assertEquals(1, repo.allStorageKeys().size)
    }

    @Test
    fun sweepDeletesAStorageObjectWithNoMatchingBlobRow() = runBlocking {
        val repo = FakeImageBlobRepository()
        val store = FakeBlobStore()
        val service = ImageBlobReclamationService(repo, store, ImageBlobConfig(), KtorSimpleLogger("test"))

        store.put("orphan/key", byteArrayOf(9), "image/jpeg")

        service.sweepOnce()

        assertTrue(!store.contains("orphan/key"))
    }
}
