package org.mr0btain.ticketman.components.data

import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

@Suppress("PropertyName")
abstract class DataSeed {
    protected var _identifier: UUID = UUIDv7.randomUUID()
    val identifier: UUID get() = _identifier

    protected var _images: MutableList<StoredImage> = mutableListOf()
    val images: List<StoredImage> get() = _images

    protected var _isNew: Boolean = true
    val isNew: Boolean get() = _isNew

    protected var _notes = mutableMapOf<Note, List<StoredImage>>()
    val notes: Map<Note, List<StoredImage>> get() = _notes.toMap()

    // Time
    protected var _time: Long = 0
    val time: Long get() = _time

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

    fun setNotes(newNotes: Map<Note, List<StoredImage>>) {
        if (_notes != newNotes) {
            _notes = newNotes.toMutableMap()
            changedFields.add(ChangedField.NOTE)
        }
    }

    fun addNote(newNote: Note, images: List<StoredImage>) {
        if (!_notes.contains(newNote)) {
            _notes[newNote] = images

            if (!changedFields.contains(ChangedField.NOTE))
                changedFields.add(ChangedField.NOTE)
        }
    }

    fun removeNote(identifier: UUID) {
        _notes.keys
            .filter { it.identifier == identifier }
            .also { foundNote ->
                _notes.remove(foundNote.first())

                if (!changedFields.contains(ChangedField.NOTE))
                    changedFields.add(ChangedField.NOTE)
            }
    }

    fun setTime(newTime: Time) {
        val new = newTime.toLong()

        if (new != _time) {
            _time = newTime.toLong()
            changedFields.add(ChangedField.TIME)
        }
    }

    fun setTime(newTime: Long) = {
        if (newTime != _time) {
            _time = newTime
            changedFields.add(ChangedField.TIME)
        }
    }

    abstract override fun toString(): String
}