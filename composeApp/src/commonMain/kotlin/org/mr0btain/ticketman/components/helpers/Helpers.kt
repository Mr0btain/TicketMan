package org.mr0btain.ticketman.components.helpers

import androidx.compose.foundation.text.input.insert
import org.mr0btain.ticketman.components.data.components.shared.AMPM
import org.mr0btain.ticketman.components.data.components.shared.Time
import org.mr0btain.ticketman.components.data.components.shared.parseAmPm
import org.mr0btain.ticketman.components.data.components.ticket.Device
import org.mr0btain.ticketman.components.data.components.ticket.TicketInfo
import org.mr0btain.ticketman.components.data.components.ticket.TicketStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatTime(millis: Long): Time {
    val milli = Date(millis)
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss a", Locale.getDefault()).format(milli).split(" ")

    return try {
        val date = format[0].split("-").map { it.toInt() }
        val time = format[1].split(":").map { it.toInt() }

        Time(
            year = date[0],
            month = date[1],
            day = date[2],
            hour = time[0],
            minute = time[1],
            second = time[2],
            parseAmPm(format[2])
        )
    } catch (e: Exception) {
        e.printStackTrace()
        Time(0,0,0,0,0,0, AMPM.PM)
    }
}

fun parseDateToLong(time: Time): Long {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss a", Locale.getDefault())

    return try {
        val date = format.parse("${time.year}-${time.month}-${time.day} ${time.hour}:${time.minute}:${time.second} ${time.ampm.name}")
        date.time
    } catch (e: Exception) {
        e.printStackTrace()
        Long.MAX_VALUE
    }
}

// TODO: Need a better way to determine if its a new ticket or not
fun TicketInfo.isNewTicket(): Boolean {
    return when {
        customerId != Int.MAX_VALUE -> false
        updates.isNotEmpty() -> false
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
