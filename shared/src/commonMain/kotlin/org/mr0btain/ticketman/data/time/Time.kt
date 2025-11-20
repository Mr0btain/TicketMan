package org.mr0btain.ticketman.data.time

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Time data class for holding the time in a usable way.
 * This might be more complicated way of doing it, but I think makes it simpler to use
 */
// TODO: Setup way to work with 24 hour time

@Serializable
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

    /**
     * Returns this time in milliseconds as a Long
     */
    fun toLong(): Long {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss a", Locale.getDefault())

        return try {
            val date = format.parse("${year}-${month}-${day} ${hour}:${minute}:${second} ${ampm.name}")
            date.time
        } catch (e: Exception) {
            e.printStackTrace()
            Long.MAX_VALUE
        }
    }

    /**
     * Returns a string in the format YEAR/MONTH/DAY
     */
    fun date(): String = "${year}/${month}/${day}"

    /**
     * Returns a string in the format HOUR:MINUTE:SECOND
     */
    fun time(): String = "${hour}:${minute}:${second}"

    companion object {
        /**
         * Parses a string into a Time
         */
        fun fromString(input: String): Time {
            // "${this.month}/${this.day}/${this.year} ${this.hour}:${this.minute}:${this.second} ${this.ampm.name}"
            val format = input.split(" ")

            val date = format
                .getOrElse(0) { "" }
                .split("/").map { it.toIntOrNull() ?: 0 }

            val time = format
                .getOrElse(1) { "" }
                .split(":")
                .map { it.toIntOrNull() ?: 0 }

            return Time(
                year = date.getOrElse(0) { 0 },
                month = date.getOrElse(1) { 0 },
                day = date.getOrElse(2) { 0 },
                hour = time.getOrElse(0) { 0 },
                minute = time.getOrElse(1) { 0 },
                second = time.getOrElse(2) { 0 },
                parseAmPm(format.getOrElse(2) { "" })
            )
        }

        /**
         * Gets th current time
         */
        fun getTime(): Time = fromLong(Calendar.getInstance().timeInMillis)

        /**
         * Takes a millisecond Long and returns it as Time
         */
        fun fromLong(input: Long): Time {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss a", Locale.getDefault())
                .format(Date(input))
                .split(" ")
                .map { it.trim() }

            val date = format
                .getOrElse(0, {""})
                .split("-").map { it.toIntOrNull() ?: 0 }

            val time = format
                .getOrElse(1, {""})
                .split(":").map { it.toIntOrNull() ?: 0 }

            return Time(
                year = date.getOrElse(0) { 0 },
                month = date.getOrElse(1) { 0 },
                day = date.getOrElse(2) { 0 },
                hour = time.getOrElse(0) { 0 },
                minute = time.getOrElse(1) { 0 },
                second = time.getOrElse(2) { 0 },
                parseAmPm(format.getOrElse(2) { "" })
            )
        }

        /**
         * Gets ampm from string
         */
        private fun parseAmPm(input: String): AMPM {
            return if (input == "AM") AMPM.AM else AMPM.PM
        }
    }

    enum class AMPM {
        AM,
        PM
    }
}