package org.mr0btain.ticketman.components.data.ticket

/** Which tickets the main screen's ticket list shows */
enum class TicketScope(val label: String) {
    /** Only tickets assigned to the logged in user */
    MINE("My tickets"),
    ALL("All tickets");

    companion object {
        val DEFAULT = ALL

        /** Saved name back to a scope, [DEFAULT] for anything unknown */
        fun byName(name: String): TicketScope = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
