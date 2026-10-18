package org.mr0btain.ticketman.components.data.ticket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.EntityReference
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketNumber
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
    var customer: EntityReference? by mutableStateOf(null)
        private set
    var user: EntityReference? by mutableStateOf(null)
        private set

    // Server owned, kept so they show and survive a round trip
    var number: TicketNumber? by mutableStateOf(null)
        private set
    var pickedUpAt: Long? by mutableStateOf(null)
        private set

    var problem: String by mutableStateOf("")
        private set
    var condition: String by mutableStateOf("")
        private set
    var accessories: String by mutableStateOf("")
        private set
    var estimateCents: Long? by mutableStateOf(null)
        private set
    var promisedAt: Long? by mutableStateOf(null)
        private set

    val ticket: Ticket
        get() = Ticket(
            identifier = identifier,
            device = device,
            status = status,
            priority = priority,
            number = number,
            problem = problem,
            condition = condition,
            accessories = accessories,
            estimateCents = estimateCents,
            promisedAt = promisedAt,
            pickedUpAt = pickedUpAt
        )

    // --- Initial setting up with data ---
    fun setTicket(
        ticket: Ticket,
        notes: List<Note> = emptyList(),
        customerId: EntityReference? = null,
        userId: EntityReference? = null
    ) {
        identifier = ticket.identifier
        device = ticket.device
        status = ticket.status
        priority = ticket.priority
        customer = customerId
        user = userId
        number = ticket.number
        pickedUpAt = ticket.pickedUpAt
        problem = ticket.problem
        condition = ticket.condition
        accessories = ticket.accessories
        estimateCents = ticket.estimateCents
        promisedAt = ticket.promisedAt
        _notes.clear()
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

    fun changeProblem(new: String) {
        if (problem != new) {
            problem = new
            _changedFields.add(ChangedField.PROBLEM)
        }
    }

    fun changeCondition(new: String) {
        if (condition != new) {
            condition = new
            _changedFields.add(ChangedField.CONDITION)
        }
    }

    fun changeAccessories(new: String) {
        if (accessories != new) {
            accessories = new
            _changedFields.add(ChangedField.ACCESSORIES)
        }
    }

    fun changeEstimate(new: Long?) {
        if (estimateCents != new) {
            estimateCents = new
            _changedFields.add(ChangedField.ESTIMATE)
        }
    }

    fun changePromisedAt(new: Long?) {
        if (promisedAt != new) {
            promisedAt = new
            _changedFields.add(ChangedField.PROMISED)
        }
    }

    fun changeCustomer(newCustomer: EntityReference?) {
        if (customer != newCustomer) {
            customer = newCustomer
            _changedFields.add(ChangedField.CUSTOMER)
        }
    }

    fun changeUser(newUser: EntityReference?) {
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
        number = null
        pickedUpAt = null
        problem = ""
        condition = ""
        accessories = ""
        estimateCents = null
        promisedAt = null
        _notes.clear()
        _images.clear()

        isNew = true
        markClean()
    }

    // Save current state as the new clean snapshot
    override fun markClean() {
        savedState = State(identifier, device, status, priority, customer, user,  notes, _images, ticket).hashCode()
        _changedFields.clear()  // Reset the changed fields
    }

    override fun toString(): String {
        return "TicketSeed(ticket=$ticket, customer=$customer, user=$user, notes=$_notes)"
    }

    private data class State(
        val identifier: Uuid = Uuid.generateV7(),
        val device:Device = Device(),
        val status: TicketStatus = TicketStatus(),
        val priority: TicketPriority = TicketPriority(),
        val customer: EntityReference? = null,
        val user: EntityReference? = null,
        val notes: List<Note> = emptyList(),
        val images: List<StoredImage> = emptyList(),
        val ticket: Ticket? = null
    )
}

