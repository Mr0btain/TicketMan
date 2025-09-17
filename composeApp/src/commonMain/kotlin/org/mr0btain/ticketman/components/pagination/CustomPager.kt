package org.mr0btain.ticketman.components.pagination

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.database.Database

class CustomPager<Key : Any, Value : Any>(
    private val initialKey: Key,
    val pageSize: Int,
    val prefetchDistance: Int = pageSize * 20,
    val maxSize: Int = 20,
    private val load: suspend (key: Key, size: Int) -> List<Value>,
    private val getNextKey: (Key, List<Value>) -> Key?
) {
    private val _items = MutableStateFlow<List<Value>>(emptyList())
    val items: StateFlow<List<Value>> = _items.asStateFlow()

    private var currentKey: Key? = initialKey
    private var isLoading = false

    suspend fun loadNextPage() {
        val key = currentKey ?: return

        if (isLoading) return

        isLoading = true

        try {
            val page = load(key, pageSize)
            if (page.isNotEmpty()) {
                _items.value = _items.value + page
            }
            currentKey = getNextKey(key, page)
        } finally {
            isLoading = false
        }
    }

    fun refresh() {
        _items.value = emptyList()
        currentKey = initialKey
    }
}