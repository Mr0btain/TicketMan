package org.mr0btain.ticketman.components.data.ticket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketStatus
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

class TicketSeed(): DataSeed() {
    var device: Device by mutableStateOf(Device())
        private set
    var status: TicketStatus by mutableStateOf(TicketStatus())
        private set
    var priority: TicketPriority by mutableStateOf(TicketPriority())
        private set
    var customer: UUID? by mutableStateOf(null)
        private set
    var user: UUID? by mutableStateOf(null)
        private set

    val ticket: Ticket
        get() = Ticket(identifier, device, status, priority)

    // --- Initial setting up with data ---
    fun setTicket(
        ticket: Ticket,
        notes: List<Note> = emptyList(),
        customerId: UUID? = null,
        userId: UUID? = null
    ) {
        identifier = ticket.identifier
        device = ticket.device
        status = ticket.status
        priority = ticket.priority
        customer = customer
        user = user

        markClean()
        isNew = false
    }

    fun setIdentifier(newId: UUID) {
        if (identifier != newId) {
            identifier = newId
            changedFields.add(ChangedField.IDENTIFIER)
        }
    }

    fun setDevice(newDevice: Device) {
        if (device != newDevice) {
            device = newDevice
            changedFields.add(ChangedField.DEVICE)
        }
    }

    fun setStatus(newStatus: TicketStatus) {
        if (status != newStatus) {
            status = newStatus
            changedFields.add(ChangedField.STATUS)
        }
    }

    fun setPriority(newPriority: TicketPriority) {
        if (priority != newPriority) {
            priority = newPriority
            changedFields.add(ChangedField.PRIORITY)
        }
    }

    fun setCustomer(newCustomer: UUID?) {
        if (customer != newCustomer) {
            customer = newCustomer
            changedFields.add(ChangedField.CUSTOMER)
        }
    }

    fun setUser(newUser: UUID?) {
        if (user != newUser) {
            user = newUser
            changedFields.add(ChangedField.USER)
        }
    }

    // Reset all fields
    override fun clear() {
        identifier = UUIDv7.randomUUID()
        device = Device()
        status = TicketStatus()
        priority = TicketPriority()
        customer = null
        user = null
        _notes.clear()
        _images.clear()
        time = UUIDv7.getLongTime(identifier)

        isNew = true
        markClean()
    }

    // Save current state as the new clean snapshot
    override fun markClean() {
        savedState = State(identifier, device, status, priority, customer, user,  notes, _images, time).hashCode()
        changedFields.clear()  // Reset the changed fields
    }

    override fun toString(): String {
        return "TicketSeed(ticket=$, customer=$customer, user=$user, notes=$_notes)"
    }

    private data class State(
        val identifier: UUID = UUIDv7.randomUUID(),
        val device:Device = Device(),
        val status: TicketStatus = TicketStatus(),
        val priority: TicketPriority = TicketPriority(),
        val customer: UUID? = null,
        val user: UUID? = null,
        val notes: List<Note> = emptyList(),
        val images: List<StoredImage> = emptyList(),
        val time: Long = 0
    )
}

