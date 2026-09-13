package org.mr0btain.ticketman.components.data.ticket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class TicketSeed: DataSeed("TicketSeed") {
    var device: Device by mutableStateOf(Device())
        private set
    var status: TicketStatus by mutableStateOf(TicketStatus())
        private set
    var priority: TicketPriority by mutableStateOf(TicketPriority())
        private set
    var customer: Identity? by mutableStateOf(null)
        private set
    var user: Identity? by mutableStateOf(null)
        private set

    val ticket: Ticket
        get() = Ticket(identifier, device, status, priority)

    // --- Initial setting up with data ---
    fun setTicket(
        ticket: Ticket,
        notes: List<Note> = emptyList(),
        customerId: Identity? = null,
        userId: Identity? = null
    ) {
        identifier = ticket.identifier
        device = ticket.device
        status = ticket.status
        priority = ticket.priority
        customer = customerId
        user = userId
        _notes.addAll(notes)

        markClean()
        isNew = false
    }

    fun changeIdentifier(newId: Uuid) {
        if (identifier != newId) {
            identifier = newId
            _changedFields.add(ChangedField.IDENTIFIER)
        }
    }

    fun changeDevice(newDevice: Device) {
        if (device != newDevice) {
            device = newDevice
            _changedFields.add(ChangedField.DEVICE)
        }
    }

    fun changeStatus(newStatus: TicketStatus) {
        if (status != newStatus) {
            status = newStatus
            _changedFields.add(ChangedField.STATUS)
        }
    }

    fun changePriority(newPriority: TicketPriority) {
        if (priority != newPriority) {
            priority = newPriority
            _changedFields.add(ChangedField.PRIORITY)
        }
    }

    fun changeCustomer(newCustomer: Identity?) {
        if (customer != newCustomer) {
            customer = newCustomer
            _changedFields.add(ChangedField.CUSTOMER)
        }
    }

    fun changeUser(newUser: Identity?) {
        if (user != newUser) {
            user = newUser
            _changedFields.add(ChangedField.USER)
        }
    }

    // Reset all fields
    override fun clear() {
        identifier = Uuid.generateV7()
        device = Device()
        status = TicketStatus()
        priority = TicketPriority()
        customer = null
        user = null
        _notes.clear()
        _images.clear()

        isNew = true
        markClean()
    }

    // Save current state as the new clean snapshot
    override fun markClean() {
        savedState = State(identifier, device, status, priority, customer, user,  notes, _images).hashCode()
        _changedFields.clear()  // Reset the changed fields
    }

    override fun toString(): String {
        return "TicketSeed(ticket=$, customer=$customer, user=$user, notes=$_notes)"
    }

    private data class State(
        val identifier: Uuid = Uuid.generateV7(),
        val device:Device = Device(),
        val status: TicketStatus = TicketStatus(),
        val priority: TicketPriority = TicketPriority(),
        val customer: Identity? = null,
        val user: Identity? = null,
        val notes: List<Note> = emptyList(),
        val images: List<StoredImage> = emptyList()
    )
}

