package org.mr0btain.ticketman.components.pagination.offset

/**
 * Config for OffsetPager
 * @param pageSize Amount of items per page
 * @param cacheSize Max amount of pages to store in the cache
 * @param prefetchDistance Amount to load ahead of current page
 */
data class OffsetPagerConfig(
    val pageSize: Int = 20,
    val cacheSize: Int = 10,
    val prefetchDistance: Int = 20,
    val initialLoadSize: Int = 4,
    val maxSize: Int = 200
)