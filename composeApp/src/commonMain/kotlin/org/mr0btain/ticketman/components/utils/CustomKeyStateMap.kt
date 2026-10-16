package org.mr0btain.ticketman.components.utils

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap

class CustomKeyStateMap<K, V>(
    private val backing: SnapshotStateMap<K, V> = mutableStateMapOf(),
    private val indexer: (K) -> Any // which field to index on
) : MutableMap<K, V> by backing {

    fun getByField(fieldValue: Any): V? {
        val key = backing.keys.firstOrNull { indexer(it) == fieldValue }
        return key?.let { backing[it] }
    }

    fun getAllByField(fieldValue: Any): List<Pair<K, V>> {
        return backing.filter { indexer(it.key) == fieldValue }
            .map { it.key to it.value }
    }
}
