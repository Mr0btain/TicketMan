package org.mr0btain.ticketman.components.data.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
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

    fun setFirstName(newName: String) {
        if (firstName != newName) {
            firstName = newName
            changedFields.add(ChangedField.NAME)
        }
    }

    fun setLastName(newName: String) {
        if (lastName != newName) {
            lastName = newName
            changedFields.add(ChangedField.NAME)
        }
    }

    fun setStreet(newStreet: String) {
        if (street != newStreet) {
            street = newStreet
            changedFields.add(ChangedField.STREET)
        }
    }

    fun setCity(newCity: String) {
        if (city != newCity) {
            city = newCity
            changedFields.add(ChangedField.CITY)
        }
    }

    fun setState(newState: String) {
        if (state != newState) {
            state = newState
            changedFields.add(ChangedField.STATE)
        }
    }

    fun setZip(newZip: String) {
        if (zip != newZip) {
            zip = newZip
            changedFields.add(ChangedField.ZIP)
        }
    }

    fun setEmail(newEmail: String) {
        if (email != newEmail) {
            email = newEmail
            changedFields.add(ChangedField.EMAIL)
        }
    }

    fun setPhone(newPhone: String) {
        if (phone != newPhone) {
            phone = newPhone
            changedFields.add(ChangedField.PHONE)
        }
    }

    fun addTicket(newTicket: Ticket) {
        if (!_tickets.contains(newTicket)) {
            _tickets.add(newTicket)
            changedFields.add(ChangedField.TICKET)
        }
    }

    fun removeTicket(ticketToRemove: UUID) {
        if (_tickets.removeIf { it == identifier }) {
            changedFields.add(ChangedField.TICKET)
        }
    }

    fun setCustomer(
        customer: Customer,
        notes: List<Note> = emptyList(),
        tickets: List<Ticket> = emptyList()
    ) {
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

        // Time
        time = UUIDv7.getLongTime(customer.identifier)

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
        time = UUIDv7.getLongTime(identifier)

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
            _images,
            time
        ).hashCode()

        changedFields.clear()
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
        val images: List<StoredImage> = emptyList(),
        val time: Long = 0
    )
}