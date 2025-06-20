package org.mr0btain.ticketman.data.shared

import java.util.Calendar

data class Time(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val ampm: AMPM
) {
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