package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.data.cache.LruCache
import kotlin.uuid.Uuid

/** Recently opened items by identifier. Main thread only, like [LruCache] */
abstract class CustomCache<T>(maxSize: Int) {
    protected val cache = LruCache<String, T>(maxSize)

    fun get(identifier: Uuid): T? =
        cache[identifier.toString()]

    /** Drops an item so the next read pulls it fresh */
    fun remove(identifier: Uuid) {
        cache.remove(identifier.toString())
    }
}
