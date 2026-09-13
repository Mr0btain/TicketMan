package org.mr0btain.ticketman.data.cache

/**
 * A Least Recently Used (LRU) cache that stores key-value pairs up to a
 * configurable maximum size.
 *
 * Entries are ordered by how recently they were accessed:
 *
 * - Reading an entry with [get] makes it the most recently used.
 * - Adding or updating an entry with [put] makes it the most recently used.
 * - When [maxSize] is exceeded, the least recently used entry is removed.
 *
 * Lookups, insertions, removals, and LRU eviction are all O(1) operations.
 *
 * @param K the type of keys maintained by this cache.
 * @param V the type of values maintained by this cache.
 * @param maxSize the maximum number of entries the cache can contain.
 */
class LruCache<K, V>(
    private val maxSize: Int,
    tag: String
) : MutableMap<K, V>, Cache(tag) {

    init {
        require(maxSize > 0) {
            "maxSize must be greater than 0"
        }
    }

    /**
     * Internal node used to maintain the doubly-linked list.
     *
     * Nodes are stored in [map] for O(1) key lookup while [prev] and [next]
     * maintain the order of least-recently-used to most-recently-used.
     */
    private class Node<K, V>(
        val key: K,
        var value: V
    ) {
        var prev: Node<K, V>? = null
        var next: Node<K, V>? = null
    }

    /**
     * Maps keys directly to their corresponding nodes.
     *
     * This allows entries to be found in O(1) time without traversing
     * the linked list.
     */
    private val map = HashMap<K, Node<K, V>>()

    /**
     * The least recently used entry.
     *
     * This entry is removed first when the cache exceeds [maxSize].
     */
    private var head: Node<K, V>? = null

    /**
     * The most recently used entry.
     *
     * Newly added and accessed entries are moved to this position.
     */
    private var tail: Node<K, V>? = null

    /**
     * Returns the current number of entries in the cache.
     */
    override val size: Int
        get() = map.size

    /**
     * Returns the value associated with [key].
     *
     * Accessing an existing entry marks it as the most recently used entry.
     *
     * @param key the key whose value should be retrieved.
     * @return the value associated with [key], or null if the key is not present.
     */
    override fun get(key: K): V? {
        val node = map[key] ?: return null

        moveToTail(node)

        return node.value
    }

    /**
     * Adds or updates an entry in the cache.
     *
     * If the key already exists, its value is updated and the entry becomes
     * the most recently used. If the key does not exist, a new entry is added.
     *
     * If adding the entry causes the cache to exceed [maxSize], the
     * least recently used entry is automatically removed.
     *
     * @param key the key to add or update.
     * @param value the value associated with [key].
     * @return the previous value associated with [key], or null if the key
     *         did not previously exist.
     */
    override fun put(key: K, value: V): V? {
        val existing = map[key]

        if (existing != null) {
            val oldValue = existing.value
            existing.value = value
            moveToTail(existing)
            return oldValue
        }

        val node = Node(key, value)

        map[key] = node
        addToTail(node)

        if (map.size > maxSize) {
            removeLeastRecentlyUsed()
        }

        return null
    }

    /**
     * Removes the entry associated with [key].
     *
     * @param key the key of the entry to remove.
     * @return the value that was associated with [key], or null if the key
     *         was not present.
     */
    override fun remove(key: K): V? {
        val node = map.remove(key) ?: return null

        unlink(node)

        return node.value
    }

    /**
     * Removes all entries from the cache and resets its LRU ordering.
     */
    override fun clear() {
        map.clear()
        head = null
        tail = null
    }

    /**
     * Returns true if the cache contains an entry for [key].
     *
     * This operation does not affect the entry's LRU position.
     *
     * @param key the key to search for.
     */
    override fun containsKey(key: K): Boolean =
        map.containsKey(key)

    /**
     * Returns true if at least one entry in the cache contains [value].
     *
     * @param value the value to search for.
     */
    override fun containsValue(value: V): Boolean =
        map.values.any { it.value == value }

    /**
     * Returns true if the cache contains no entries.
     */
    override fun isEmpty(): Boolean =
        map.isEmpty()

    /**
     * Returns the set of keys currently stored in the cache.
     */
    override val keys: MutableSet<K>
        get() = map.keys

    /**
     * Returns the collection of values currently stored in the cache.
     */
    override val values: MutableCollection<V>
        get() = map.values.mapTo(mutableListOf()) { it.value }

    /**
     * Returns the entries currently stored in the cache.
     */
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = map.values
            .mapTo(mutableSetOf()) { node ->
                object : MutableMap.MutableEntry<K, V> {

                    override val key: K
                        get() = node.key

                    override val value: V
                        get() = node.value

                    /**
                     * Updates the value stored by this entry.
                     *
                     * @param newValue the new value to associate with the key.
                     * @return the previous value.
                     */
                    override fun setValue(newValue: V): V {
                        val old = node.value
                        node.value = newValue
                        return old
                    }
                }
            }

    /**
     * Adds all entries from [from] to the cache.
     *
     * Entries are added in the iteration order of [from]. Each entry becomes
     * the most recently used entry when it is added.
     *
     * @param from the map whose entries should be added.
     */
    override fun putAll(from: Map<out K, V>) {
        from.forEach { (key, value) ->
            put(key, value)
        }
    }

    /**
     * Adds [node] to the end of the linked list, making it the
     * most recently used entry.
     */
    private fun addToTail(node: Node<K, V>) {
        node.prev = tail
        node.next = null

        if (tail != null) {
            tail!!.next = node
        } else {
            head = node
        }

        tail = node
    }

    /**
     * Moves [node] to the end of the linked list, making it the
     * most recently used entry.
     *
     * Does nothing if the node is already the most recently used entry.
     */
    private fun moveToTail(node: Node<K, V>) {
        if (node === tail) {
            return
        }

        unlink(node)
        addToTail(node)
    }

    /**
     * Removes [node] from its current position in the linked list.
     *
     * This operation does not remove the node from [map].
     */
    private fun unlink(node: Node<K, V>) {
        val prev = node.prev
        val next = node.next

        if (prev != null) {
            prev.next = next
        } else {
            head = next
        }

        if (next != null) {
            next.prev = prev
        } else {
            tail = prev
        }

        node.prev = null
        node.next = null
    }

    /**
     * Removes the least recently used entry from the cache.
     *
     * The least recently used entry is always located at [head].
     */
    private fun removeLeastRecentlyUsed() {
        val node = head ?: return

        map.remove(node.key)
        unlink(node)
    }
}

