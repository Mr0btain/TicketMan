package org.mr0btain.ticketman.components.data.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.utils.splitName
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.logging.AppLogger
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.name_divider_character
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
        get() = _name.lastOrNull() ?: ""

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
        get() = Customer(identifier, _name.joinToString(Res.string.name_divider_character.toString()), address, email, phone)

    fun changeName(newName: List<String>) {
        if (_name != newName) {
            _name.clear()
            _name.addAll(newName)
            _changedFields.add(ChangedField.NAME)
        }
    }

    fun changeStreet(newStreet: String) {
        if (street != newStreet) {
            street = newStreet
            _changedFields.add(ChangedField.STREET)
        }
    }

    fun changeCity(newCity: String) {
        if (city != newCity) {
            city = newCity
            _changedFields.add(ChangedField.CITY)
        }
    }

    fun changeState(newState: String) {
        if (state != newState) {
            state = newState
            _changedFields.add(ChangedField.STATE)
        }
    }

    fun changeZip(newZip: String) {
        newZip.filter { it.isDigit() }.also { result ->
            if (zip != result) {
                zip = result
                _changedFields.add(ChangedField.ZIP)
            }
        }

    }

    fun changeEmail(newEmail: String) {
        newEmail.filter { !it.isWhitespace() }.also { result ->
            if (email != result) {
                email = result
                _changedFields.add(ChangedField.EMAIL)
            }
        }

    }

    fun changePhone(newPhone: String) {
        newPhone.filter { it.isDigit() }.also { result ->
            if (phone != result) {
                phone = result
                _changedFields.add(ChangedField.PHONE)
            }
        }
    }

    fun addTicket(newTicket: Ticket) {
        _tickets.indexOfFirst { newTicket.identifier == it.identifier }.also { index ->
            if (index == -1) {
                _tickets.add(newTicket)
                _changedFields.add(ChangedField.TICKET)
            } else {
                AppLogger.m(ADD_TICKET_TAG, "Ticket already exists")
            }
        }
    }

    fun deleteTicket(ticketToRemove: Uuid) {
        _tickets.indexOfFirst { it.identifier == ticketToRemove }.also { index ->
            if (index != -1) {
                _tickets.removeAt(index)
                _changedFields.add(ChangedField.TICKET)
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
    }

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

    override fun markClean() {
        savedState = State(
            identifier,
            name.joinToString(Res.string.name_divider_character.toString()),
            street,
            city,
            state,
            zip,
            email,
            phone,
            _tickets,
            _notes,
            _images
        ).hashCode()

        _changedFields.clear()
    }

    override fun toString(): String =
        "CustomerSeed(Name=${splitName(customer.name).joinToString(" ")}, Address=$street $city $state $zip, Email=$email, Phone=$phone, Tickets=${_tickets.toList()}, Notes=${_notes.toList()})"

    private data class State(
        val identifier: Uuid = Uuid.generateV7(),
        val name: String = "",
        val street: String = "",
        val city: String = "",
        val state: String = "",
        val zip: String = "",
        val email: String = "",
        val phone: String = "",
        val tickets: List<Ticket> = emptyList(),
        val notes: List<Note> = emptyList(),
        val images: List<ImageBitmap> = emptyList()
    )
}