package com.tenmilelabs.infrastructure.storage

import com.tenmilelabs.domain.repository.BlobStore
import java.io.ByteArrayInputStream
import java.io.InputStream

class FakeBlobStore : BlobStore {
    private val objects = mutableMapOf<String, ByteArray>()

    fun contains(key: String): Boolean = objects.containsKey(key)

    override suspend fun put(key: String, bytes: ByteArray, mimeType: String) {
        objects[key] = bytes
    }

    override suspend fun openRead(key: String): InputStream? = objects[key]?.let { ByteArrayInputStream(it) }

    override suspend fun delete(key: String) {
        objects.remove(key)
    }

    override suspend fun listAllKeys(): List<String> = objects.keys.toList()
}
