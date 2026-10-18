package org.mr0btain.ticketman.components.data.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.components.utils.splitName
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CustomerSeed: DataSeed("CustomerSeed") {
    private val ADD_TICKET_TAG = "ADD_TICKET"
    private val DELETE_TICKET_TAG = "DELETE_TICKET"

    // Name
    private val _name = mutableStateListOf("")
    val name: List<String>
        get() = _name

    val firstName: String
        get() = _name.firstOrNull() ?: ""
    val lastName: String
        get() = if (_name.size > 1) _name.last() else ""

    // Address
    var street by mutableStateOf("")
        private set
    var city by mutableStateOf("")
        private set
    var state by mutableStateOf("")
        private set
    var zip by mutableStateOf("")
        private set

    // Contact info
    var email by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set

    // Tickets
    private val _tickets = mutableStateListOf<Ticket>()
    val tickets: List<Ticket> = _tickets

    val address: Address
        get() = Address(street, city, state, zip)

    val customer: Customer
        get() = Customer(identifier, joinName(_name), address, email, phone)

    fun changeName(newName: List<String>) {
        if (_name.toList() != newName) {
            _name.clear()
            _name.addAll(newName)
        }
    }

    fun changeStreet(newStreet: String) {
        street = newStreet
    }

    fun changeCity(newCity: String) {
        city = newCity
    }

    fun changeState(newState: String) {
        state = newState
    }

    fun changeZip(newZip: String) {
        zip = newZip.filter { it.isDigit() }
    }

    fun changeEmail(newEmail: String) {
        email = newEmail.filter { !it.isWhitespace() }
    }

    fun changePhone(newPhone: String) {
        phone = newPhone.filter { it.isDigit() }
    }

    fun addTicket(newTicket: Ticket) {
        _tickets.indexOfFirst { newTicket.identifier == it.identifier }.also { index ->
            if (index == -1) {
                _tickets.add(newTicket)
            } else {
                AppLogger.m(ADD_TICKET_TAG, "Ticket already exists")
            }
        }
    }

    fun deleteTicket(ticketToRemove: Uuid) {
        _tickets.indexOfFirst { it.identifier == ticketToRemove }.also { index ->
            if (index != -1) {
                _tickets.removeAt(index)
            } else {
                AppLogger.m(DELETE_TICKET_TAG, "Ticket not found")
            }
        }
    }

    fun setCustomer(
        customer: Customer,
        notes: List<Note> = emptyList(),
        tickets: List<Ticket> = emptyList()
    ) {
        // Identifier
        identifier = customer.identifier

        // Name
        _name.clear()
        _name.addAll(splitName(customer.name))

        // Address
        street = customer.address.street
        city = customer.address.city
        state = customer.address.state
        zip = customer.address.zip

        // Contact
        phone = customer.phone
        email = customer.email

        _notes.clear()
        _notes.addAll(notes)

        _tickets.clear()
        _tickets.addAll(tickets)

        markClean() // Mark clean
        isNew = false
    }

    override fun fields() = mapOf(
        ChangedField.NAME to _name.toList(),
        ChangedField.STREET to street,
        ChangedField.CITY to city,
        ChangedField.STATE to state,
        ChangedField.ZIP to zip,
        ChangedField.EMAIL to email,
        ChangedField.PHONE to phone,
        ChangedField.TICKET to _tickets.toList()
    )

    override fun clear() {
        identifier = Uuid.generateV7()
        _name.clear()
        street = ""
        city = ""
        state = ""
        zip = ""
        email = ""
        phone = ""
        _tickets.clear()
        _notes.clear()
        _images.clear()

        isNew = true
        markClean()
    }

    override fun toString(): String =
        "CustomerSeed(Name=${splitName(customer.name).joinToString(" ")}, Address=$street $city $state $zip, Email=$email, Phone=$phone, Tickets=${_tickets.toList()}, Notes=${_notes.toList()})"
}