package org.mr0btain.ticketman.data.cache

import kotlin.test.*

class LruCacheTest {
    @Test
    fun evictsTheLeastRecentlyUsed() {
        val cache = LruCache<String, Int>(2)
        cache.put("a", 1)
        cache.put("b", 2)

        // Reading a makes b the oldest
        assertEquals(1, cache["a"])
        cache.put("c", 3)

        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
        assertEquals(2, cache.size)
    }

    @Test
    fun updatingAKeyKeepsOneEntryAndReturnsTheOldValue() {
        val cache = LruCache<String, Int>(2)
        assertNull(cache.put("a", 1))
        assertEquals(1, cache.put("a", 2))

        assertEquals(2, cache["a"])
        assertEquals(1, cache.size)
    }

    @Test
    fun removeAndClear() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)

        assertEquals(1, cache.remove("a"))
        assertFalse(cache.containsKey("a"))

        cache.clear()
        assertEquals(0, cache.size)

        // Still works after clearing
        cache.put("c", 3)
        assertEquals(3, cache["c"])
    }

    @Test
    fun shrinkingDropsTheLeastRecentlyUsed() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        cache["a"]

        cache.resize(2)

        assertEquals(2, cache.size)
        assertFalse(cache.containsKey("b"))
        assertTrue(cache.containsKey("a"))

        // Growing keeps what's there and makes room
        cache.resize(4)
        cache.put("d", 4)
        cache.put("e", 5)
        assertEquals(4, cache.size)
    }
}
