package org.mr0btain.ticketman.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A simple key-value cache with no size limitations.
 *
 * @param Key Type of keys.
 * @param Value Type of values.
 */
class SimpleCache<Key, Value>() {
    private val cache = mutableMapOf<Key, Value>()
    private val mutex = Mutex()

    /** Gets a value by key, or returns null if not present. */
    suspend fun get(key: Key): Value? = mutex.withLock {
        cache[key]
    }

    /** Puts a key-value pair into the cache. Overwrites if key exists. */
    suspend fun put(key: Key, value: Value) = mutex.withLock {
        cache[key] = value
    }

    /** Gets the value or computes and stores it using the [loader] if missing. */
    suspend fun getOrPutSuspend(key: Key, loader: suspend () -> Value): Value = mutex.withLock {
        cache[key] ?: loader().also {
            cache[key] = it
        }
    }

    /** Removes a value by key. */
    suspend fun remove(key: Key) = mutex.withLock {
        cache.remove(key)
    }

    /** Clears the entire cache. */
    suspend fun clear() = mutex.withLock {
        cache.clear()
    }

    /** Returns the current size of the cache. */
    suspend fun size(): Int = mutex.withLock {
        cache.size
    }

    /** Checks whether the cache contains a given key. */
    suspend fun containsKey(key: Key): Boolean = mutex.withLock {
        cache.containsKey(key)
    }
}
