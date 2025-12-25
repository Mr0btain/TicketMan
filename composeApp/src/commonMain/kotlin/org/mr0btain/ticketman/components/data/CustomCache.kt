package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.data.cache.LruCache
import java.util.*

abstract class CustomCache<T>(tag: String, maxSize: Int) {
    protected val cache = LruCache<UUID, T>(maxSize, tag)

    suspend fun get(identifier: UUID): T? =
        cache.get(identifier)
}