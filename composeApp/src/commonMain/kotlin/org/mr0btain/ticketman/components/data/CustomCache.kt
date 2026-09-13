package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.data.cache.LruCache
import kotlin.uuid.Uuid

abstract class CustomCache<T>(tag: String, maxSize: Int) {
    protected val cache = LruCache<Uuid, T>(maxSize, tag)

    suspend fun get(identifier: Uuid): T? =
        cache.get(identifier)
}