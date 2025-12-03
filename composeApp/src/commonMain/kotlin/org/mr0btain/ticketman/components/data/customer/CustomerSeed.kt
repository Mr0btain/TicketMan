package org.mr0btain.ticketman.components.data.customer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
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
    private var _email = mutableStateListOf<String>()
    private var _phone = mutableStateListOf<String>()

    // Tickets
    private var _tickets = mutableStateListOf<UUID>()

    val email: List<String> = _email
    val phone: List<String> = _phone
    val tickets: List<UUID> = _tickets

    // snapshot of the last saved state
    private var savedState: State = State()

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

    fun addEmail(newEmail: String) {
        if (!_email.contains(newEmail)) {
            _email.add(newEmail)
            changedFields.add(ChangedField.EMAIL)
        }
    }

    fun removeEmail(emailToRemove: String) {
        if (_email.contains(emailToRemove)) {
            _email.removeIf { it == emailToRemove }
            changedFields.add(ChangedField.EMAIL)
        }
    }

    fun addPhone(newPhone: String) {
        if (!_phone.contains(newPhone)) {
            _phone.add(newPhone)
            changedFields.add(ChangedField.PHONE)
        }
    }

    fun removePhone(phoneToRemove: String) {
        if (_phone.contains(phoneToRemove)) {
            _phone.removeIf { it == phoneToRemove }
            changedFields.add(ChangedField.PHONE)
        }
    }

    fun addTicket(newTicket: UUID) {
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
        notes: Map<Note, List<StoredImage>> = emptyMap(),
        tickets: List<UUID> = emptyList()
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
        _phone.clear()
        _phone.addAll(customer.contactInfo.phone)
        _email.clear()
        _email.addAll(customer.contactInfo.email)

        setNotes(notes)

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
        _email.clear()
        _phone.clear()
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
            _email,
            _phone,
            _tickets,
            _notes,
            _images,
            time
        )

        changedFields.clear()
    }

    override fun toString(): String =
        "CustomerSeed(Name=$firstName $lastName, Address=$street $city $state $zip, Email=${_email.toList()}, Phone=${_phone.toList()}, Tickets=${_tickets.toList()}, Notes=${_notes.toList()})"

    private data class State(
        val identifier: UUID = UUIDv7.randomUUID(),
        val firstName: String = "",
        val lastName: String = "",
        val street: String = "",
        val city: String = "",
        val state: String = "",
        val zip: String = "",
        val email: List<String> = emptyList(),
        val phone: List<String> = emptyList(),
        val tickets: List<UUID> = emptyList(),
        val notes: Map<Note, List<StoredImage>> = emptyMap(),
        val images: List<StoredImage> = emptyList(),
        val time: Long = 0
    )
}