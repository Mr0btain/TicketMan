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
        identifier = newId
    }

    fun changeDevice(newDevice: Device) {
        device = newDevice
    }

    fun changeStatus(newStatus: TicketStatus) {
        status = newStatus
    }

    fun changePriority(newPriority: TicketPriority) {
        priority = newPriority
    }

    fun changeProblem(new: String) {
        problem = new
    }

    fun changeCondition(new: String) {
        condition = new
    }

    fun changeAccessories(new: String) {
        accessories = new
    }

    fun changeEstimate(new: Long?) {
        estimateCents = new
    }

    fun changePromisedAt(new: Long?) {
        promisedAt = new
    }

    fun changeCustomer(newCustomer: EntityReference?) {
        customer = newCustomer
    }

    fun changeUser(newUser: EntityReference?) {
        user = newUser
    }

    override fun fields() = mapOf(
        ChangedField.DEVICE to device,
        ChangedField.STATUS to status,
        ChangedField.PRIORITY to priority,
        ChangedField.PROBLEM to problem,
        ChangedField.CONDITION to condition,
        ChangedField.ACCESSORIES to accessories,
        ChangedField.ESTIMATE to estimateCents,
        ChangedField.PROMISED to promisedAt,
        ChangedField.CUSTOMER to customer,
        ChangedField.USER to user
    )

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

    override fun toString(): String {
        return "TicketSeed(ticket=$ticket, customer=$customer, user=$user, notes=$_notes)"
    }
}

