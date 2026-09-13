package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Note
import kotlin.uuid.Uuid

abstract class Database<T> {
    abstract fun add(new: T): Boolean
    abstract fun update(item: T): Boolean
    abstract fun get(id: Uuid): T?
    abstract fun getNotes(id: Uuid): List<Note>?

    abstract fun getPagedItems(offset: Int, limit: Int): List<T>
    abstract fun delete(id: Uuid): Boolean
    abstract fun addNotes(notes: List<Note>, id: Uuid): Boolean

    abstract fun search(query: String, max: Int): List<SearchResult>
}