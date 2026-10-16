package org.mr0btain.ticketman.components.data.local

/**
 * Map backed [KeyValueStore] for tests so nothing touches disk or the platform key stores
 */
class InMemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String, defaultValue: String): String = values[key] ?: defaultValue
    override fun putString(key: String, value: String) {
        values[key] = value
    }
    override suspend fun delete(key: String) {
        values.remove(key)
    }
}
