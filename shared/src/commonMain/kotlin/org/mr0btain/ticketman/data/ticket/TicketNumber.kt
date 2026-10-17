package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable

/**
 * Readable ticket number like 26-00000042. The server hands these out, the sequence starts over each year.
 * @param year Full year, e.g. 2026
 * @param sequence Count within that year, starting at 1
 */
@Serializable
data class TicketNumber(
    val year: Int,
    val sequence: Long
) {
    override fun toString(): String =
        "${(year % 100).toString().padStart(2, '0')}-${sequence.toString().padStart(SEQUENCE_DIGITS, '0')}"

    companion object {
        const val SEQUENCE_DIGITS = 8
        private val FULL = Regex("""^(\d{2})-(\d{1,8})$""")

        /**
         * Reads "26-00000042" or "26-42". Two digit years are taken as 20xx
         */
        fun parse(text: String): TicketNumber? {
            val match = FULL.matchEntire(text.trim()) ?: return null
            val (year, sequence) = match.destructured

            return TicketNumber(2000 + year.toInt(), sequence.toLong())
        }
    }
}
