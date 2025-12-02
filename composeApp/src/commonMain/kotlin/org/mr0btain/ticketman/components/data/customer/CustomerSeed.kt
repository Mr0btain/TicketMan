package org.mr0btain.ticketman.components.data.customer

import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

class CustomerSeed: DataSeed() {
    // Name
    private var _firstName: String = ""
    private var _lastName: String = ""

    // Address
    private var _street: String = ""
    private var _city: String = ""
    private var _state: String = ""
    private var _zip: String = ""

    // Contact info
    private var _email: MutableList<String> = mutableListOf()
    private var _phone: MutableList<String> = mutableListOf()

    // Tickets
    private var _tickets: MutableList<Ticket> = mutableListOf()

    val name: PersonName get() = PersonName(_firstName, _lastName)
    val firstName: String get() = _firstName
    val lastName: String get() = _lastName

    val address: Address get() = Address(_street, _city, _state, _zip)
    val street: String get() = _street
    val city: String get() = _city
    val state: String get() = _state
    val zip: String get() = _zip

    val email: List<String> get() = _email.toList()
    val phone: List<String> get() = _phone.toList()
    val tickets: List<Ticket> get() = _tickets.toList()

    // snapshot of the last saved state
    private var savedState: State = State()

    fun setFirstName(newName: String) {
        if (_firstName != newName) {
            _firstName = newName
            changedFields.add(ChangedField.NAME)
        }
    }

    fun setLastName(newName: String) {
        if (_lastName != newName) {
            _lastName = newName
            changedFields.add(ChangedField.NAME)
        }
    }

    fun setStreet(newStreet: String) {
        if (_street != newStreet) {
            _street = newStreet
            changedFields.add(ChangedField.STREET)
        }
    }

    fun setCity(newCity: String) {
        if (_city != newCity) {
            _city = newCity
            changedFields.add(ChangedField.CITY)
        }
    }

    fun setState(newState: String) {
        if (_state != newState) {
            _state = newState
            changedFields.add(ChangedField.STATE)
        }
    }

    fun setZip(newZip: String) {
        if (_zip != newZip) {
            _zip = newZip
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

    fun addTicket(newTicket: Ticket) {
        if (!_tickets.contains(newTicket)) {
            _tickets.add(newTicket)
            changedFields.add(ChangedField.TICKET)
        }
    }

    fun removeTicket(ticketToRemove: UUID) {
        if (_tickets.removeIf { it.identifier == identifier }) {
            changedFields.add(ChangedField.TICKET)
        }
    }

    fun setCustomer(
        customer: Customer,
        notes: Map<Note, List<StoredImage>> = emptyMap(),
        tickets: List<Ticket> = emptyList()
    ) {
        // Name
        _firstName = customer.name.firstName
        _lastName = customer.name.lastName

        // Address
        _street = customer.address.street
        _city = customer.address.city
        _state = customer.address.state
        _zip = customer.address.zip

        // Contact
        _phone = customer.contactInfo.phone.toMutableList()
        _email = customer.contactInfo.email.toMutableList()

        setNotes(notes)

        _tickets = tickets.toMutableList()

        // Time
        _time = UUIDv7.getLongTime(customer.identifier)

        markClean() // Mark clean
    }

    override fun clear() {
        _identifier = UUIDv7.randomUUID()
        _firstName = ""
        _lastName = ""
        _street = ""
        _city = ""
        _state = ""
        _zip = ""
        _email.clear()
        _phone.clear()
        _tickets.clear()
        _notes.clear()
        _images.clear()
        _time = UUIDv7.getLongTime(_identifier)

        _isNew = true
        markClean()
    }

    override fun markClean() {
        savedState = State(
            _identifier,
            _firstName,
            _lastName,
            _street,
            _city,
            _state,
            _zip,
            _email,
            _phone,
            _tickets,
            _notes,
            _images,
            _time
        )

        changedFields.clear()
    }

    override fun toString(): String =
        "CustomerSeed(Name=$_firstName $_lastName, Address=$_street $_city $_state $_zip, Email=$_email, Phone=$_phone, Tickets=$_tickets, Notes=$_notes)"

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
        val tickets: List<Ticket> = emptyList(),
        val notes: Map<Note, List<StoredImage>> = emptyMap(),
        val images: List<StoredImage> = emptyList(),
        val time: Long = 0
    )
}