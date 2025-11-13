package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.ticket.Ticket
import java.util.UUID

class TicketSeed() {
    private var _ticket: Ticket? = null
    private var _customer: Identity? = null
    private var _user: Identity? = null
    private var _notes = mutableListOf<NoteData>()
    private var _isNew: Boolean = true

    val ticket: Ticket? get() = _ticket
    val customer: Identity? get() = _customer
    val user: Identity? get() = _user
    val notes: List<NoteData> get() = _notes.toList()
    val isNew: Boolean get() = _isNew

    // snapshot of the last saved state
    private var savedState: State = State()

    // List to store the names of fields that have changed
    private val changedFields = mutableListOf<DataType>()

    // Computed property to check if the object has changed
    val isDirty: Boolean
        get() = changedFields.isNotEmpty()

    // --- Initial setting up ---
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
            changedFields.add(DataType.TICKET)
        }
    }

    fun setNotes(newNotes: List<NoteData>) {
        if (_notes != newNotes) {
            _notes = newNotes.toMutableList()
            changedFields.add(DataType.NOTE)
        }
    }

    fun addNote(newNote: NoteData) {
        if (!_notes.contains(newNote)) {
            _notes.add(newNote)

            if (!changedFields.contains(DataType.NOTE))
                changedFields.add(DataType.NOTE)
        }
    }

    fun removeNote(identifier: UUID) {
        if (_notes.removeIf { it.note.identifier == identifier })
            changedFields.add(DataType.NOTE)
    }

    fun setCustomer(newCustomer: Identity?) {
        if (_customer != newCustomer) {
            _customer = newCustomer
            changedFields.add(DataType.CUSTOMER)
        }
    }

    fun setUser(newUser: Identity?) {
        if (_user != newUser) {
            _user = newUser
            changedFields.add(DataType.USER)
        }
    }

    // Reset all fields
    fun clear() {
        _ticket = null
        _customer = null
        _user = null
        _notes.clear()
        _isNew = true

        markClean()
    }

    // Save current state as the new clean snapshot
    fun markClean() {
        savedState = State(_ticket, _customer, _user, _notes)
        changedFields.clear()  // Reset the changed fields
    }

    // Return the list of changed fields
    fun getChangedFields(): List<DataType> {
        return changedFields.toList()
    }

    /*
    // Not sure if we need to do this or not right now
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as TicketSeed
        return _ticket == other._ticket &&
                _customer == other._customer &&
                _user == other._user &&
                _notes == other._notes
    }

    override fun hashCode(): Int {
        var result = _ticket?.hashCode() ?: 0
        result = 31 * result + (_customer?.hashCode() ?: 0)
        result = 31 * result + (_user?.hashCode() ?: 0)
        result = 31 * result + _notes.hashCode()
        return result
    }
     */

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

