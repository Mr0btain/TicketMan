package org.mr0btain.ticketman.components.pagination.cursor

data class CursorPageResult<Key, T>(
    val items: List<T>,
    val prevCursor: Key?,
    val nextCursor: Key?
)