package org.mr0btain.ticketman.data.time

import org.mr0btain.ticketman.data.SubItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Time(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val ampm: AMPM
): SubItem {
    override fun toString(): String {
        return "${this.month}/${this.day}/${this.year} ${this.hour}:${this.minute}:${this.second} ${this.ampm.name}"
    }
}

enum class AMPM {
    AM,
    PM
}

fun parseAmPm(input: String): AMPM {
    return if (input == "AM") AMPM.AM else AMPM.PM
}

fun getTime(): Long {
    return Calendar.getInstance().timeInMillis
}

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

fun Time.tolong(): Long {
    return parseDateToLong(this)
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