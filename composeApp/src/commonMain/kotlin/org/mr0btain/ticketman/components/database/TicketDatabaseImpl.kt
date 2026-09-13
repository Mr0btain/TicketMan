package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketStatus
import kotlin.uuid.Uuid

class TicketDatabaseImpl: Database<Ticket>() {
    override fun add(new: Ticket): Boolean {
        TODO("Not yet implemented")
    }

    override fun update(item: Ticket): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(id: Uuid): Ticket? {
        TODO("Not yet implemented")
    }

    override fun getNotes(id: Uuid): List<Note>? {
        TODO("Not yet implemented")
    }

    override fun getPagedItems(
        offset: Int,
        limit: Int
    ): List<Ticket> {
        TODO("Not yet implemented")
    }

    fun getCustomer(id: Uuid): Identity? {
        TODO("Not yet implemented")
    }

    fun getUser(id: Uuid): Identity? {
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
    ): List<SearchResult> {
        TODO("Not yet implemented")
    }

    fun updateStatus(id: Uuid, status: TicketStatus): Boolean {
        TODO("Not yet implemented")
    }

    fun updateDevice(id: Uuid, device: Device): Boolean {
        TODO("Not yet implemented")
    }

    fun updatePriority(id: Uuid, priority: TicketPriority): Boolean {
        TODO("Not yet implemented")
    }
}