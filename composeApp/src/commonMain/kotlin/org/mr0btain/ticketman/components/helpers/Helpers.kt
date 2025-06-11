package org.mr0btain.ticketman.components.helpers

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

fun TicketInfo.isNewTicket(): Boolean {
    return when {
        customerId != Int.MAX_VALUE -> false
        updates.isNotEmpty() -> false
        device != Device("", "") -> false
        assignedEmployee != Int.MAX_VALUE -> false
        else -> true
    }
}
