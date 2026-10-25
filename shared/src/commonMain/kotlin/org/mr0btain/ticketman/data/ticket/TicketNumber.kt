package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable

/**
 * Readable ticket number like 26-00000042, or DT-26-00000042 at a location with a code. The server hands these
 * out, each location counting on its own and starting over each year.
 * @param year Full year, e.g. 2026
 * @param sequence Count within that year, starting at 1
 * @param prefix The code of the location it was made at, blank for the main location
 */
@Serializable
data class TicketNumber(
    val year: Int,
    val sequence: Long,
    val prefix: String = ""
) {
    override fun toString(): String =
        (if (prefix.isBlank()) "" else "$prefix-") +
            "${(year % 100).toString().padStart(2, '0')}-${sequence.toString().padStart(SEQUENCE_DIGITS, '0')}"

    companion object {
        const val SEQUENCE_DIGITS = 8
        private val FULL = Regex("""^(?:([A-Za-z0-9]{1,4})-)?(\d{2})-(\d{1,8})$""")

        /**
         * Reads "26-00000042", "26-42" or "DT-26-42". Two digit years are taken as 20xx, codes as capitals
         */
        fun parse(text: String): TicketNumber? {
            val match = FULL.matchEntire(text.trim()) ?: return null
            val (prefix, year, sequence) = match.destructured

            return TicketNumber(2000 + year.toInt(), sequence.toLong(), prefix.uppercase())
        }
    }
}
