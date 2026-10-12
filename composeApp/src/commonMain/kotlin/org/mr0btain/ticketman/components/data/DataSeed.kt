package org.mr0btain.ticketman.components.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.time.createdAt
import org.mr0btain.ticketman.logging.AppLogger
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: (Setup Image handling in here as well.)

@Suppress("PropertyName")
abstract class DataSeed(tag: String) {
    protected val DELETE_NOTE_TAG = "$tag:DELETE_NOTE"
    protected val UPDATE_NOTE_TAG = "$tag:UPDATE_NOTE"

    @OptIn(ExperimentalUuidApi::class)
    var identifier: Uuid by mutableStateOf(Uuid.generateV7())
        protected set

    protected val _images = mutableStateListOf<ImageBitmap>()
    val images: List<ImageBitmap> = _images

    var isNew: Boolean by mutableStateOf(true)
        protected set

    protected var _notes = mutableStateListOf<Note>()
    val notes: List<Note> = _notes

    // List to store the names of fields that have changed
    protected val _changedFields = mutableListOf<ChangedField>()

    fun init() {
        clear()
    }

    // Computed property to check if the object has changed
    val isDirty: Boolean
        get() = _changedFields.isNotEmpty()

    val time: Instant
        get() = identifier.createdAt()

    // snapshot of the last saved state
    protected var savedState: Int = 0

    // Reset all fields
    abstract fun clear()

    // Save current state as the new clean snapshot
    abstract fun markClean()

    // Return the list of changed fields
    fun getChangedFields(): List<ChangedField> = _changedFields

    fun setNotes(newNotes: List<Note>) {
        if (_notes != newNotes) {
            _notes.clear()
            _notes.addAll(newNotes)

            _changedFields.add(ChangedField.NOTE)
        }
    }

    fun addNote(newNote: Note) {
        if (!_notes.contains(newNote)) {
            _notes.add(newNote)

            if (!_changedFields.contains(ChangedField.NOTE))
                _changedFields.add(ChangedField.NOTE)
        }
    }


    fun deleteNote(identifier: Uuid) {
        _notes.indexOfFirst { it.identifier == identifier }.also { index ->
            if (index != -1) {
                _notes.removeAt(index)

                if (!_changedFields.contains(ChangedField.NOTE))
                    _changedFields.add(ChangedField.NOTE)
            } else {
                AppLogger.m(DELETE_NOTE_TAG, "Note not found")
            }
        }
    }

    /**
     * Gets the note for the specified id if it exists.
     */
    fun getNote(identifier: Uuid): Note? =
        _notes.firstOrNull { it.identifier == identifier }

    /**
    Essentially just replaces the old note with this one.
     */
    fun updateNote(note: Note) {
        _notes.indexOfFirst { it.identifier == note.identifier }.also { index ->
            if (index != -1) {
                _notes[index] = note
            } else {
                AppLogger.m(UPDATE_NOTE_TAG, "Note not found")
            }
        }
    }

    abstract override fun toString(): String
}