package org.mr0btain.ticketman.components.data.components

import java.util.Calendar

data class Time(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val ampm: AMPM
)

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