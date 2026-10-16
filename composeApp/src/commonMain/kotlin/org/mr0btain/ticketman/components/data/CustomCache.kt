package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.data.cache.LruCache
import kotlin.uuid.Uuid

abstract class CustomCache<T>(maxSize: Int, tag: String) {
    protected val cache = LruCache<String, T>(maxSize, tag)

    suspend fun get(identifier: Uuid): T? =
        cache[identifier.toString()]
}