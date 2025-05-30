package org.mr0btain.ticketman.components.data.components

enum class SubItemType {
                       ID,
    TICKET,
    EMAIL,
    PHONE,
    NOTE,
    UPDATE,
    NONE
}

fun getLabelTextForType(subItemType: SubItemType): String {
    return when (subItemType) {
        SubItemType.ID -> "Id"
        SubItemType.TICKET -> "Ticket"
        SubItemType.EMAIL -> "Email"
        SubItemType.PHONE -> "Phone"
        SubItemType.NOTE -> "Note"
        SubItemType.UPDATE -> "Update"
        SubItemType.NONE -> "ERROR"
    }
}