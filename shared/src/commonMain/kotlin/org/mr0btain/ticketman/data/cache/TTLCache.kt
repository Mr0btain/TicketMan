package org.mr0btain.ticketman.data.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe Time-To-Live (TTL) cache using ConcurrentHashMap.
 *
 * Each entry in the cache is valid for a fixed time ([ttlMillis]) after being added.
 * Expired items are automatically removed when accessed via [get] or [getOrPut].
 *
 * @param Key Type of the cache key.
 * @param Value Type of the cache value.
 * @param ttlMillis Time-to-live for each cache entry in milliseconds.
 */
class TTLCache<Key, Value>(private val ttlMillis: Long) {

    /** Internal data class to store both value and expiration timestamp. */
    private data class CacheItem<V>(val value: V, val expirationTime: Long)

    // Thread-safe map to store cache items
    private val map = ConcurrentHashMap<Key, CacheItem<Value>>()

    /** Returns the value for the given [key] if it's not expired, or null if missing/expired. */
    fun get(key: Key): Value? {
        val item = map[key]
        if (item != null && System.currentTimeMillis() < item.expirationTime) {
            return item.value // Value is still valid
        }
        map.remove(key) // Expired entry is cleaned up
        return null
    }

    /** Adds or replaces a value for the given [key], with a new expiration time. */
    fun put(key: Key, value: Value) {
        val expirationTime = System.currentTimeMillis() + ttlMillis
        map[key] = CacheItem(value, expirationTime)
    }

    /** Gets the value if present and not expired, or computes/stores a new one using [defaultValue]. */
    fun getOrPut(key: Key, defaultValue: () -> Value): Value {
        val existing = get(key)
        if (existing != null) return existing

        val newValue = defaultValue()
        put(key, newValue)
        return newValue
    }

    /** Removes the entry associated with the given [key], if present. */
    fun remove(key: Key) {
        map.remove(key)
    }

    /** Clears the entire cache. */
    fun clear() {
        map.clear()
    }
}
