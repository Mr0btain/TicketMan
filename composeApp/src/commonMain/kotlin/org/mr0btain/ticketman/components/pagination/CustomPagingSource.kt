package org.mr0btain.ticketman.components.pagination

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.database.Database

class CustomPagingSource<T: MainItem>(
    private val repository: Database<T>,
    private val pageSize: Int = 40
) : PagingSource<Long, T>() {
    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, T> {
        val page = params.key ?: 1L

        return try {
            val response = repository.getPagedItems((page - 1) * pageSize, pageSize) ?: emptyList()

            LoadResult.Page(
                data = response,
                prevKey = if (page == 1L) null else page - 1,
                nextKey = if (response.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, T>): Long? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
