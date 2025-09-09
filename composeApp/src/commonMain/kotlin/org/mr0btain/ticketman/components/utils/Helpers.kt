package org.mr0btain.ticketman.components.utils

import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.Ticket

// TODO: Need a better way to determine if its a new ticket or not
fun Ticket.isNewTicket(): Boolean {
    return when {
        //customerId.isEmpty() -> false
        //updates.isNotEmpty() -> false
        device != Device("", "") -> false
        else -> true
    }
}

fun formatIntToMoney(input: Int): String {
    val builder = StringBuilder(input.toString())

    builder.apply {
        //val amountToDrop = builder.length - dropWhile { it == '0' }.length
        //replace(0, amountToDrop, "")
        when (length) {
            0 -> append("000")
            1 -> insert(0, "00")
            2 -> insert(0, "0")
        }

        insert(length - 2, ".")
    }

    return builder.toString()
}

fun getNameForDataType(dataType: DataType, subDataType: DataType): String {
    return "${dataType.stringName}'s ${subDataType.stringName}"
}
