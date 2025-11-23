package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.components.data.note.NoteData
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.UUID

@Suppress("PropertyName")
abstract class DataSeed {
    protected var _identifier: UUID = UUIDv7.randomUUID()
    val identifier: UUID get() = _identifier

    protected var _images: MutableList<StoredImage> = mutableListOf()
    val images: List<StoredImage> get() = _images

    protected var _isNew: Boolean = true
    val isNew: Boolean get() = _isNew

    protected var _notes = mutableListOf<NoteData>()
    val notes: List<NoteData> get() = _notes.toList()

    // List to store the names of fields that have changed
    protected val changedFields = mutableListOf<ChangedField>()

    // Computed property to check if the object has changed
    val isDirty: Boolean
        get() = changedFields.isNotEmpty()

    // Reset all fields
    abstract fun clear()

    // Save current state as the new clean snapshot
    abstract fun markClean()

    // Return the list of changed fields
    fun getChangedFields(): List<ChangedField> {
        return changedFields.toList()
    }

    fun setNotes(newNotes: List<NoteData>) {
        if (_notes != newNotes) {
            _notes = newNotes.toMutableList()
            changedFields.add(ChangedField.NOTE)
        }
    }

    fun addNote(newNote: NoteData) {
        if (!_notes.contains(newNote)) {
            _notes.add(newNote)

            if (!changedFields.contains(ChangedField.NOTE))
                changedFields.add(ChangedField.NOTE)
        }
    }

    fun removeNote(identifier: UUID) {
        if (_notes.removeIf { it.note.identifier == identifier })
            changedFields.add(ChangedField.NOTE)
    }

    abstract override fun toString(): String
}