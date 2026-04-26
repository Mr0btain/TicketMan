package org.mr0btain.ticketman.components.data.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

class CustomerSeed: DataSeed() {
    // Name
    var firstName by mutableStateOf("")
        private set
    var lastName by mutableStateOf("")
        private set

    val name: String
        get() = "$firstName $lastName"

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
    private var _tickets = mutableStateListOf<Ticket>()
    val tickets: List<Ticket> = _tickets

    val address: Address
        get() = Address(street, city, state, zip)

    val customer: Customer
        get() = Customer(identifier, PersonName(firstName, lastName), address, email, phone)

    fun changeFirstName(newName: String) {
        if (firstName != newName) {
            firstName = newName
            _changedFields.add(ChangedField.NAME)
        }
    }

    fun changeLastName(newName: String) {
        if (lastName != newName) {
            lastName = newName
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
        if (zip != newZip) {
            zip = newZip
            _changedFields.add(ChangedField.ZIP)
        }
    }

    fun changeEmail(newEmail: String) {
        if (email != newEmail) {
            email = newEmail
            _changedFields.add(ChangedField.EMAIL)
        }
    }

    fun changePhone(newPhone: String) {
        if (phone != newPhone) {
            phone = newPhone
            _changedFields.add(ChangedField.PHONE)
        }
    }

    fun addTicket(newTicket: Ticket) {
        if (!_tickets.contains(newTicket)) {
            _tickets.add(newTicket)
            _changedFields.add(ChangedField.TICKET)
        }
    }

    fun removeTicket(ticketToRemove: UUID) {
        if (_tickets.removeIf { it == identifier }) {
            _changedFields.add(ChangedField.TICKET)
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
        firstName = customer.name.firstName
        lastName = customer.name.lastName

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
        identifier = UUIDv7.randomUUID()
        firstName = ""
        lastName = ""
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
            firstName,
            lastName,
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
        "CustomerSeed(Name=$firstName $lastName, Address=$street $city $state $zip, Email=$email, Phone=$phone, Tickets=${_tickets.toList()}, Notes=${_notes.toList()})"

    private data class State(
        val identifier: UUID = UUIDv7.randomUUID(),
        val firstName: String = "",
        val lastName: String = "",
        val street: String = "",
        val city: String = "",
        val state: String = "",
        val zip: String = "",
        val email: String = "",
        val phone: String = "",
        val tickets: List<Ticket> = emptyList(),
        val notes: List<Note> = emptyList(),
        val images: List<StoredImage> = emptyList()
    )
}