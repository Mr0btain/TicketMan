package org.mr0btain.ticketman.components.data.customer

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.customer.Address
import java.util.*

data class CustomerData(
    val customer: Customer = Customer(),
    val notes: List<NoteData> = emptyList(),
    val tickets: List<TicketData> = emptyList()
)

/**
 * Removes an email address from CustomerData
 * @param emailToRemove The string email /  element to remove
 */
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
fun CustomerData.removeTicket(identifier: UUID): CustomerData {
    val updatedTickets = tickets.filter { it.ticket.identifier != identifier }
    return copy(tickets = updatedTickets)
}

/**
 * Removes a phone number from CustomerData
 * @param phoneToRemove The string number / element to remove
 */
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
fun CustomerData.removeNote(identifier: UUID): CustomerData {
    val updatesNotes = notes.filter { it.note.identifier != identifier }
    return copy(notes = updatesNotes)
}

/**
 * Clears the address field of CustomerData
 */
fun CustomerData.clearAddress(): CustomerData {
    val clearedAddressCustomer = customer.copy(address = Address())
    return copy(customer = clearedAddressCustomer)
}