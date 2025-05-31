package org.mr0btain.ticketman.components.data.components

enum class SubItemType {
                       ID,
    TICKET,
    EMAIL,
    PHONE,
    NOTE,
    UPDATE,
    CUSTOMER_ID,
    ASSIGNED_EMPLOYEE,
    DEVICE,
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
        SubItemType.CUSTOMER_ID -> "Customer Id"
        SubItemType.ASSIGNED_EMPLOYEE -> "Assigned Employee"
        SubItemType.DEVICE -> "Device"
        SubItemType.NONE -> "ERROR"
    }
}