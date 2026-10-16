package org.mr0btain.ticketman.components.data.local

import eu.anifantakis.lib.ksafe.KSafe

/**
 * Simple key value store so [LocalStorage] isn't tied to KSafe and can be swapped for an in memory one in tests
 */
interface KeyValueStore {
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)
    suspend fun delete(key: String)
}

/**
 * Encrypted store backed by KSafe. Uses the direct calls since ktor's token loading isn't suspending,
 * they read from KSafe's in memory cache and write to disk in the background
 */
class KSafeKeyValueStore(private val ksafe: KSafe) : KeyValueStore {
    override fun getString(key: String, defaultValue: String): String = ksafe.getDirect(key, defaultValue)
    override fun putString(key: String, value: String) = ksafe.putDirect(key, value)
    override suspend fun delete(key: String) = ksafe.delete(key)
}
