package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.UUID

class TicketSeed(): DataSeed() {
    private var _device: Device = Device()
    private var _status: TicketStatus = TicketStatus()
    private var _priority: TicketPriority = TicketPriority()
    private var _customer: Identity? = null
    private var _user: Identity? = null

    val device: Device get() = _device
    val status: TicketStatus get() = _status
    val priority: TicketPriority get() = _priority
    val customer: Identity? get() = _customer
    val user: Identity? get() = _user

    // snapshot of the last saved state
    private var savedState: State = State()

    // --- Initial setting up with data ---
    fun setInitial(
        ticket: Ticket,
        notes: Map<Note, List<StoredImage>>,
        customer: Identity? = null,
        user: Identity? = null
    ) {
        _identifier = ticket.identifier
        _device = ticket.device
        _status = ticket.status
        _priority = ticket.priority
        _notes = notes.toMutableMap()
        _customer = customer
        _user = user

        markClean()
        _isNew = false
    }

    fun setIdentifier(newId: UUID) {
        if (_identifier != newId) {
            _identifier = newId
            changedFields.add(ChangedField.IDENTIFIER)
        }
    }

    fun setDevice(newDevice: Device) {
        if (_device != newDevice) {
            _device = newDevice
            changedFields.add(ChangedField.DEVICE)
        }
    }

    fun setStatus(newStatus: TicketStatus) {
        if (_status != newStatus) {
            _status = newStatus
            changedFields.add(ChangedField.STATUS)
        }
    }

    fun setPriority(newPriority: TicketPriority) {
        if (_priority != newPriority) {
            _priority = newPriority
            changedFields.add(ChangedField.PRIORITY)
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
        _identifier = UUIDv7.randomUUID()
        _device = Device()
        _status = TicketStatus()
        _priority = TicketPriority()
        _customer = null
        _user = null
        _notes.clear()
        _images.clear()
        _time = UUIDv7.getLongTime(_identifier)

        _isNew = true
        markClean()
    }

    // Save current state as the new clean snapshot
    override fun markClean() {
        savedState = State(_identifier, _device, _status, _priority, _customer, _user,  _notes, _images, _time)
        changedFields.clear()  // Reset the changed fields
    }

    override fun toString(): String {
        return "TicketSeed(ticket=$, customer=$_customer, user=$_user, notes=$_notes)"
    }

    private data class State(
        val identifier: UUID = UUIDv7.randomUUID(),
        val device:Device = Device(),
        val status: TicketStatus = TicketStatus(),
        val priority: TicketPriority = TicketPriority(),
        val customer: Identity? = null,
        val user: Identity? = null,
        val notes: Map<Note, List<StoredImage>> = emptyMap(),
        val images: List<StoredImage> = emptyList(),
        val time: Long = 0
    )
}

