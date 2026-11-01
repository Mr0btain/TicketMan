package org.mr0btain.ticketman.components.data.ticket

import org.mr0btain.ticketman.data.ticket.ChecklistItem
import org.mr0btain.ticketman.data.ticket.Ticket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What goes back in an update has to include what the server would otherwise overwrite */
class TicketSeedSaveTest {
    private val loaded = Ticket(problem = "Cracked", checklist = listOf(ChecklistItem("Powers on")), revision = 7)

    @Test
    fun editingKeepsTheIntakeChecklistAndRevision() {
        val seed = TicketSeed().apply {
            setTicket(loaded)
            changeProblem("Cracked and bent")
        }

        assertEquals(loaded.checklist, seed.ticket.checklist)
        assertEquals(7, seed.ticket.revision)
        assertEquals("Cracked and bent", seed.ticket.problem)
    }

    @Test
    fun aNewTicketStartsWithNeither() {
        val seed = TicketSeed().apply {
            setTicket(loaded)
            clear()
        }

        assertEquals(emptyList(), seed.ticket.checklist)
        assertNull(seed.ticket.revision)
    }
}
