package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.User
import kotlin.uuid.Uuid

class UserDatabaseImpl: Database<User>() {
    override fun add(new: User): Boolean {
        TODO("Not yet implemented")
    }

    override fun update(item: User): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(id: Uuid): User? {
        TODO("Not yet implemented")
    }

    override fun getNotes(id: Uuid): List<Note>? {
        TODO("Not yet implemented")
    }

    override fun getPagedItems(
        offset: Int,
        limit: Int
    ): List<User> {
        TODO("Not yet implemented")
    }

    override fun delete(item: Uuid): Boolean {
        TODO("Not yet implemented")
    }

    override fun addNotes(
        notes: List<Note>,
        id: Uuid
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun search(
        query: String,
        max: Int
    ): List<User> {
        TODO("Not yet implemented")
    }
}