package org.mr0btain.ticketman.components.data.customer

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.CustomerDatabaseImpl
import org.mr0btain.ticketman.error.SimpleResult
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class CustomerData @OptIn(ExperimentalUuidApi::class) constructor(
    val customer: Customer = Customer(),
    val notes: List<NoteData> = emptyList(),
    val tickets: List<TicketData> = emptyList()
)

/**
 * Removes an email address from CustomerData
 * @param emailToRemove The string email /  element to remove
 */
@OptIn(ExperimentalUuidApi::class)
fun CustomerData.removeEmail(emailToRemove: String): CustomerData {
    val updatedEmails = customer.contactInfo.email.filter { it != emailToRemove }
    val updatedContactInfo = customer.contactInfo.copy(email = updatedEmails)
    val updatedCustomer = customer.copy(contactInfo = updatedContactInfo)
    return copy(customer = updatedCustomer)
}

/**
 * Removes a ticket from CustomerData
 * @param identifier The ticket to be removed identifier
 */
@OptIn(ExperimentalUuidApi::class)
fun CustomerData.removeTicket(identifier: Uuid): CustomerData {
    val updatedTickets = tickets.filter { it.ticket.identifier != identifier }
    return copy(tickets = updatedTickets)
}

/**
 * Removes a phone number from CustomerData
 * @param phoneToRemove The string number / element to remove
 */
@OptIn(ExperimentalUuidApi::class)
fun CustomerData.removePhoneNumber(phoneToRemove: String): CustomerData {
    val updatedPhoneNumbers = customer.contactInfo.phone.filter { it != phoneToRemove }
    val updatedContactInfo = customer.contactInfo.copy(phone = updatedPhoneNumbers)
    val updatedCustomer = customer.copy(contactInfo = updatedContactInfo)
    return copy(customer = updatedCustomer)
}

/**
 * Removes a Note from CustomerData
 * @param identifier The note to be removed identifier
 */
@OptIn(ExperimentalUuidApi::class)
fun CustomerData.removeNote(identifier: Uuid): CustomerData {
    val updatesNotes = notes.filter { it.note.identifier != identifier }
    return copy(notes = updatesNotes)
}

/**
 * Clears the address field of CustomerData
 */
@OptIn(ExperimentalUuidApi::class)
fun CustomerData.clearAddress(): CustomerData {
    val clearedAddressCustomer = customer.copy(address = Address())
    return copy(customer = clearedAddressCustomer)
}