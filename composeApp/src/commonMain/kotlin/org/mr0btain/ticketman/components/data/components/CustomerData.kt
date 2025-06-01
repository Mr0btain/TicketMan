package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.Serializable

@Serializable
data class CustomerData(
    val info: CustomerInfo = CustomerInfo(),
    val tickets: List<String> = listOf(),
    val notes: List<Note> = listOf()
)

/**
 * Only used to avoid issue with updating data after passing the state. for some reason we end up with a bad comparison because of snapshot state
 */

fun CustomerData.compare(data: CustomerData): Boolean {
    return when {
        // For sure checks for lists
        this.tickets.toList() != data.tickets.toList() -> false
        this.notes.toList() != data.notes.toList() -> false

        //Address
        this.info.address.state != data.info.address.state -> false
        this.info.address.street != data.info.address.street -> false
        this.info.address.city != data.info.address.city -> false
        this.info.address.zip != data.info.address.zip -> false

        // Name
        this.info.name.firstName != data.info.name.firstName -> false
        this.info.name.lastName != data.info.name.lastName -> false

        //Contact
        this.info.contactInfo.email.toList() != data.info.contactInfo.email.toList() -> false
        this.info.contactInfo.phone.toList() != data.info.contactInfo.phone.toList() -> false

        this.info.identifier != data.info.identifier -> false

        else -> true
    }
}

/**
Only used for serializing into json for database
 */
@Serializable
data class TicketList(val list: List<String> = listOf())