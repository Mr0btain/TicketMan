package org.mr0btain.ticketman.components.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.operations.ChangedField
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

    protected var _notes = mutableStateMapOf<Note, List<StoredImage>>()
    val notes: Map<Note, List<StoredImage>> = _notes

    // Time
    var time: Long by mutableStateOf(0)
        protected set

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
            _notes.clear()
            _notes.putAll(newNotes)
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

    fun getNote(identifier: UUID): Pair<Note, List<StoredImage>>? {
        val found = notes.keys.firstOrNull { it.identifier == identifier }

        return if (found != null) {
            Pair(found, notes[found] ?: emptyList())
        } else null
    }

    abstract override fun toString(): String
}