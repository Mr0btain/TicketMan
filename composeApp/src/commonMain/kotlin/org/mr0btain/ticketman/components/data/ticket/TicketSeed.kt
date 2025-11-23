package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.ticket.Ticket
import java.util.UUID

// TODO: We could make this handle the entirety of Ticket, with all the priority and such,

class TicketSeed(): DataSeed() {
    private var _ticket: Ticket? = null
    private var _customer: Identity? = null
    private var _user: Identity? = null

    val ticket: Ticket? get() = _ticket
    val customer: Identity? get() = _customer
    val user: Identity? get() = _user

    // snapshot of the last saved state
    private var savedState: State = State()

    // --- Initial setting up with data ---
    fun setInitial(
        data: TicketData,
        customer: Identity? = null,
        user: Identity? = null
    ) {
        _ticket = data.ticket
        _notes = data.notes.toMutableList()
        _customer = customer
        _user = user

        markClean()
        _isNew = false
    }

    // -- Setters that automatically mark changes --
    fun setTicket(newTicket: Ticket) {
        if (_ticket != newTicket) {
            _ticket = newTicket
            changedFields.add(ChangedField.TICKET)
        }
    }

    fun setCustomer(newCustomer: Identity?) {
        if (_customer != newCustomer) {
            _customer = newCustomer
            changedFields.add(ChangedField.CUSTOMER)
        }
    }

    fun setUser(newUser: Identity?) {
        if (_user != newUser) {
            _user = newUser
            changedFields.add(ChangedField.USER)
        }
    }

    // Reset all fields
    override fun clear() {
        _ticket = null
        _customer = null
        _user = null
        _notes.clear()
        _isNew = true

        markClean()
    }

    // Save current state as the new clean snapshot
    override fun markClean() {
        savedState = State(_ticket, _customer, _user, _notes)
        changedFields.clear()  // Reset the changed fields
    }

    override fun toString(): String {
        return "TicketSeed(ticket=$_ticket, customer=$_customer, user=$_user, notes=$_notes)"
    }

    private data class State(
        val ticket: Ticket? = null,
        val customer: Identity? = null,
        val user: Identity? = null,
        val notes: List<NoteData> = emptyList()
    )
}

