package org.mr0btain.ticketman.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mr0btain.ticketman.error.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import java.util.LinkedHashMap

/**
 * A thread-safe Least Recently Used (LRU) cache implementation for coroutine-based Kotlin applications.
 *
 * This cache automatically evicts the least recently accessed item when the number of items exceeds [maxSize].
 * Access to the cache is protected using a coroutine-safe [Mutex] to ensure consistency across concurrent operations.
 *
 * @param Key Type of the cache key.
 * @param Value Type of the cache value.
 * @param maxSize Maximum number of items to retain in the cache.
 */
class LruCache<Key, Value>(
    private val maxSize: Int,
    private val tag: String = "LruCache"
): Cache(tag) {
    /**
     * Internal LinkedHashMap with `accessOrder = true` to maintain access order (LRU behavior).
     * When entries are accessed (via get/put), they move to the end of the map.
     * The oldest entry (least recently used) is at the front.
     *
     * The `removeEldestEntry()` method is overridden to automatically remove
     * the eldest entry when the size exceeds [maxSize].
     */
    private val cache = object : LinkedHashMap<Key, Value>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Value>): Boolean {
            return size > maxSize
        }
    }

    /** A coroutine-safe mutex to ensure atomic operations when multiple coroutines access the cache.*/
    private val mutex = Mutex()

    /**
     * Retrieves the value associated with [key], or null if not present.
     * Access is synchronized with [mutex] for thread safety.
     */
    suspend fun get(key: Key): Value? = mutex.withLock {
        cache[key]?.also {
            AppLogger.m(GET_TAG, SimpleResult.SUCCESS)
        }
    }

    /**
     * Puts the given [key] and [value] into the cache.
     * If the key already exists, it updates the value and moves the entry to the end (most recently used).
     */
    suspend fun put(key: Key, value: Value) = mutex.withLock {
        cache[key] = value
        AppLogger.m(PUT_TAG, "Put: $key")
    }

    /**
     * Retrieves the value for the given [key] if it exists, or computes and stores it using [loader] if not.
     * This supports lazy-loading with suspendable computations.
     */
    suspend fun getOrPutSuspend(key: Key, loader: suspend () -> Value): Value = mutex.withLock {
        cache[key] ?: loader().also {
            cache[key] = it
        }
    }

    /** Removes the value associated with the given [key] from the cache. */
    suspend fun remove(key: Key) = mutex.withLock {
        cache.remove(key)
        AppLogger.m(REMOVE_TAG, "Removed: $key")
    }

    /** Clears all entries from the cache. */
    suspend fun clear() = mutex.withLock {
        cache.clear()
        AppLogger.m(CLEAR_TAG, SimpleResult.SUCCESS)
    }
}
