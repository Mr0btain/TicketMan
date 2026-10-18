package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.ticket.Ticket
import kotlin.test.*

class DataSeedTest {
    private val note = Note(author = "Sam", message = "Screen ordered")

    private fun seedWithNote() = TicketSeed().apply { setTicket(Ticket(), listOf(note)) }

    @Test
    fun editingANoteMarksTheNotesChanged() {
        val seed = seedWithNote()
        assertFalse(seed.isDirty)

        // Adding a photo to a saved note has to reach the server on save, not just the screen
        seed.saveNote(note.copy(images = listOf(StoredImage(width = 40, height = 30))))

        assertTrue(seed.isDirty)
        assertTrue(ChangedField.NOTE in seed.getChangedFields())
        assertEquals(1, seed.notes.single().images.size)
    }

    @Test
    fun updatingANoteThatIsntThereChangesNothing() {
        val seed = seedWithNote()

        seed.updateNote(Note(message = "Not on this ticket"))

        assertFalse(seed.isDirty)
        assertEquals(listOf(note), seed.notes)
    }
}
