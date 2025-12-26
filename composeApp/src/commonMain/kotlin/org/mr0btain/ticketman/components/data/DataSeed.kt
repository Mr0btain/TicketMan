package org.mr0btain.ticketman.components.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.customer.CustomerSeed.State
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.utils.CustomKeyStateMap
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

@Suppress("PropertyName")
abstract class DataSeed {
    var identifier: UUID by mutableStateOf(UUIDv7.randomUUID())
        protected set

    protected val _images = mutableStateListOf<StoredImage>()
    val images: List<StoredImage> = _images

    var isNew: Boolean by mutableStateOf(true)
        protected set

    protected var _notes = mutableStateListOf<Note>()
    val notes: List<Note> = _notes

    // Time
    var time: Long by mutableStateOf(0)
        protected set

    // List to store the names of fields that have changed
    protected val changedFields = mutableListOf<ChangedField>()

    // Computed property to check if the object has changed
    val isDirty: Boolean
        get() = changedFields.isNotEmpty()

    // snapshot of the last saved state
    protected var savedState: Int = 0

    // Reset all fields
    abstract fun clear()

    // Save current state as the new clean snapshot
    abstract fun markClean()

    // Return the list of changed fields
    fun getChangedFields(): List<ChangedField> {
        return changedFields.toList()
    }

    fun setNotes(newNotes: List<Note>) {
        if (_notes != newNotes) {
            _notes.clear()
            _notes.addAll(newNotes)

            changedFields.add(ChangedField.NOTE)
        }
    }

    fun addNote(newNote: Note) {
        if (!_notes.contains(newNote)) {
            _notes.add(newNote)

            if (!changedFields.contains(ChangedField.NOTE))
                changedFields.add(ChangedField.NOTE)
        }
    }

    fun removeNote(identifier: UUID) {
        if (_notes.removeIf { it.identifier == identifier }) {
            if (!changedFields.contains(ChangedField.NOTE))
                changedFields.add(ChangedField.NOTE)
        }
    }

    /*
    fun setTime(newTime: Time) {
        val new = newTime.toLong()

        if (new != time) {
            time = newTime.toLong()
            changedFields.add(ChangedField.TIME)
        }
    }

    fun setTime(newTime: Long) = {
        if (newTime != time) {
            time = newTime
            changedFields.add(ChangedField.TIME)
        }
    }
     */

    fun getNote(identifier: UUID): Note? =
        _notes.firstOrNull { it.identifier == identifier }

    fun editNote(note: Note) {
        _notes.removeIf { it.identifier == note.identifier }
        _notes.add(note)
    }

    abstract override fun toString(): String
}