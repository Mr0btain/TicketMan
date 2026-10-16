package org.mr0btain.ticketman.data.cache

/*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A simple coroutine-safe fixed-size cache that evicts the oldest inserted entry
 * when the size exceeds [maxSize]. This is NOT an LRU cache.
 *
 * @param Key Type of the cache key.
 * @param Value Type of the cache value.
 * @param maxSize Maximum number of items to store in the cache.
 */
class FixedSizeCache<Key, Value>(
    private val maxSize: Int
) {
    /** LinkedHashMap with insertion order (default) so we can evict the oldest inserted item. */
    private val cache = object : LinkedHashMap<Key, Value>() {
        override fun put(key: Key, value: Value): Value? {
            val result = super.put(key, value)
            if (size > maxSize) {
                val firstKey = entries.first().key
                remove(firstKey)
            }
            return result
        }
    }

    private val mutex = Mutex()

    /** Retrieves a value by key, or null if it doesn't exist. */
    suspend fun get(key: Key): Value? = mutex.withLock {
        cache[key]
    }

    /** Inserts or updates a key-value pair. Evicts the oldest entry if size limit is exceeded. */
    suspend fun put(key: Key, value: Value) = mutex.withLock {
        cache.put(key, value)
    }

    /** Retrieves the value for the key, or loads and stores it using [loader] if absent. */
    suspend fun getOrPutSuspend(key: Key, loader: suspend () -> Value): Value = mutex.withLock {
        cache[key] ?: loader().also {
            cache[key] = it
        }
    }

    /** Removes a value by key. */
    suspend fun remove(key: Key) = mutex.withLock {
        cache.remove(key)
    }

    /** Clears all entries in the cache. */
    suspend fun clear() = mutex.withLock {
        cache.clear()
    }

    /** Returns the current number of entries. */
    suspend fun size(): Int = mutex.withLock {
        cache.size
    }
}
 */
