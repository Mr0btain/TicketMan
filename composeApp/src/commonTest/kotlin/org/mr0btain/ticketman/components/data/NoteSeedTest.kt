package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import kotlin.test.*

class NoteSeedTest {
    private val photo = StoredImage(description = "Cracked back", width = 40, height = 30)
    private val note = Note(author = "Sam", message = "Screen ordered", images = listOf(photo))

    @Test
    fun settingANoteLeavesItClean() {
        val seed = NoteSeed(note)

        assertFalse(seed.isDirty)
        assertFalse(seed.isNew)
        assertEquals(note, seed.note)
    }

    @Test
    fun eachChangeIsTracked() {
        val seed = NoteSeed(note)

        seed.changeMessage("Screen fitted")
        seed.changeAuthor("Alex")

        assertEquals(setOf(ChangedField.MESSAGE, ChangedField.AUTHOR), seed.getChangedFields().toSet())
        assertEquals(Note(note.identifier, "Alex", "Screen fitted", listOf(photo)), seed.note)
    }

    @Test
    fun settingTheSameValueIsNotAChange() {
        val seed = NoteSeed(note)

        seed.changeMessage(note.message)
        seed.changeImages(note.images)
        seed.addImage(photo)

        assertFalse(seed.isDirty)
    }

    @Test
    fun changingSomethingBackIsUnchanged() {
        val seed = NoteSeed(note)

        seed.changeMessage("Screen fitted")
        seed.deleteImage(photo.identifier)
        assertTrue(seed.isDirty)

        seed.changeMessage(note.message)
        seed.addImage(photo)
        assertFalse(seed.isDirty)
        assertTrue(seed.getChangedFields().isEmpty())
    }

    @Test
    fun aPhotoIsAddedOnceEvenIfItsDescriptionChanged() {
        val seed = NoteSeed(note)

        seed.addImage(photo.copy(description = "Edited"))

        assertEquals(1, seed.images.size)
        assertFalse(seed.isDirty)
    }

    @Test
    fun removingAPhoto() {
        val seed = NoteSeed(note)

        seed.deleteImage(photo.identifier)

        assertTrue(seed.images.isEmpty())
        assertEquals(setOf(ChangedField.IMAGE), seed.getChangedFields().toSet())
    }

    @Test
    fun removingAPhotoThatIsntThereChangesNothing() {
        val seed = NoteSeed(note)

        seed.deleteImage(StoredImage().identifier)

        assertEquals(listOf(photo), seed.images)
        assertFalse(seed.isDirty)
    }

    @Test
    fun clearStartsANewNote() {
        val seed = NoteSeed(note)
        seed.changeMessage("Half typed")

        seed.clear()

        assertTrue(seed.isNew)
        assertFalse(seed.isDirty)
        assertNotEquals(note.identifier, seed.identifier)
        assertEquals("", seed.message)
        assertTrue(seed.images.isEmpty())
    }
}
