package org.mr0btain.ticketman.components.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.time.createdAt
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A record being looked at or edited. What changed is worked out by comparing its fields against a snapshot taken
 * when it was loaded or saved, so changing something back to how it was counts as unchanged.
 *
 * Each field is Compose state, so screens update as it changes. A subclass holds its record's fields with a
 * change function for each, lists them in [fields], loads a record with a set function that ends in [markClean],
 * and builds the record back up for saving. Notes and photos are kept here and count towards [isDirty] too; they
 * only reach the server when the record is saved.
 * @param tag Logging tag
 */
@Suppress("PropertyName")
abstract class DataSeed(tag: String) {
    protected val DELETE_NOTE_TAG = "$tag:DELETE_NOTE"
    protected val UPDATE_NOTE_TAG = "$tag:UPDATE_NOTE"

    /** The record's identifier. A new record gets its final one here, before it's ever saved */
    @OptIn(ExperimentalUuidApi::class)
    var identifier: Uuid by mutableStateOf(Uuid.generateV7())
        protected set

    protected val _images = mutableStateListOf<StoredImage>()
    /** Photos of the record itself, as opposed to photos on its notes. Only inventory items have them */
    val images: List<StoredImage> = _images

    /** True until a saved record is loaded into it, so saving adds rather than updates */
    var isNew: Boolean by mutableStateOf(true)
        protected set

    protected var _notes = mutableStateListOf<Note>()
    /** The record's notes, including ones added or changed here that haven't been saved yet */
    val notes: List<Note> = _notes

    /** When the record was made, read from its identifier */
    val time: Instant
        get() = identifier.createdAt()

    // Fields as they were when last loaded or saved
    private var saved: Map<ChangedField, Any?> by mutableStateOf(emptyMap())

    /**
     * The record's editable fields as they are now. Lists have to be copies, so later edits don't change the
     * saved snapshot too
     */
    protected abstract fun fields(): Map<ChangedField, Any?>

    private fun snapshot(): Map<ChangedField, Any?> =
        fields() + mapOf(
            ChangedField.IDENTIFIER to identifier,
            ChangedField.NOTE to _notes.toList(),
            ChangedField.IMAGE to _images.toList()
        )

    /** The notes as they were when last loaded or saved, to tell this device's changes from another's */
    @Suppress("UNCHECKED_CAST")
    val savedNotes: List<Note>
        get() = saved[ChangedField.NOTE] as? List<Note> ?: emptyList()

    /** Whether anything differs from when it was last loaded or saved */
    val isDirty: Boolean
        get() = snapshot() != saved

    /** Fields that differ from when it was last loaded or saved */
    fun getChangedFields(): List<ChangedField> {
        val now = snapshot()
        return now.keys.filter { now[it] != saved[it] }
    }

    /** Empties every field and starts a new record with a new identifier, clean */
    abstract fun clear()

    /** Takes the fields as they are now as the saved state */
    fun markClean() {
        saved = snapshot()
    }

    /** Replaces the notes. Only changes state if they differ, so it doesn't redraw for nothing */
    fun setNotes(newNotes: List<Note>) {
        if (_notes.toList() != newNotes) {
            _notes.clear()
            _notes.addAll(newNotes)
        }
    }

    /** Replaces the record's own photos, in the order given */
    fun setImages(newImages: List<StoredImage>) {
        if (_images.toList() != newImages) {
            _images.clear()
            _images.addAll(newImages)
        }
    }

    /** Adds a photo that was just uploaded, unless it's already here */
    fun addImage(image: StoredImage) {
        if (_images.none { it.identifier == image.identifier }) setImages(_images + image)
    }

    /** Drops a photo. The server marks it deleted when the record is saved */
    @OptIn(ExperimentalUuidApi::class)
    fun removeImage(identifier: Uuid) {
        setImages(_images.filter { it.identifier != identifier })
    }

    /** Adds a note, unless the same one is already here */
    fun addNote(newNote: Note) {
        if (!_notes.contains(newNote)) _notes.add(newNote)
    }

    /** Drops a note. The server deletes it when the record is saved */
    fun deleteNote(identifier: Uuid) {
        val index = _notes.indexOfFirst { it.identifier == identifier }

        if (index != -1) _notes.removeAt(index)
        else AppLogger.m(DELETE_NOTE_TAG, "Note not found")
    }

    /** The note with this identifier, if it's here */
    fun getNote(identifier: Uuid): Note? =
        _notes.firstOrNull { it.identifier == identifier }

    /** Adds [note], or replaces the one with the same identifier if it's already here */
    fun saveNote(note: Note) {
        if (getNote(note.identifier) != null) updateNote(note) else addNote(note)
    }

    /** Replaces the note with the same identifier */
    fun updateNote(note: Note) {
        val index = _notes.indexOfFirst { it.identifier == note.identifier }

        if (index != -1) _notes[index] = note
        else AppLogger.m(UPDATE_NOTE_TAG, "Note not found")
    }

    /** For logging, with the fields spelled out */
    abstract override fun toString(): String
}
