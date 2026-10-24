package org.mr0btain.ticketman.components.data.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.time.createdAt
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A note being written or edited. Like the record seeds, what changed is worked out against a snapshot taken when
 * it was set, so changing something back counts as unchanged
 */
@OptIn(ExperimentalUuidApi::class)
class NoteSeed {
    // Constructor to set initial data from an existing note if we need to.
    constructor(initial: Note? = null) {
        initial?.let {
            setNote(initial)
        }
    }

    var identifier: Uuid by mutableStateOf(Uuid.generateV7())
        private set

    private val _images = mutableStateListOf<StoredImage>()
    val images: List<StoredImage> = _images

    var isNew: Boolean by mutableStateOf(true)
        private set

    val time: Instant
        get() = identifier.createdAt()

    var author by mutableStateOf("")
        private set

    var message: String by mutableStateOf("")
        private set

    var customerVisible: Boolean by mutableStateOf(false)
        private set

    // Fields as they were when the note was set
    private var saved: Map<ChangedField, Any?> by mutableStateOf(emptyMap())

    private fun snapshot(): Map<ChangedField, Any?> = mapOf(
        ChangedField.IDENTIFIER to identifier,
        ChangedField.AUTHOR to author,
        ChangedField.MESSAGE to message,
        ChangedField.IMAGE to _images.toList(),
        ChangedField.CUSTOMER_VISIBLE to customerVisible
    )

    /** Whether anything differs from when the note was set */
    val isDirty: Boolean
        get() = snapshot() != saved

    val note: Note
        get() = Note(identifier, author, message, _images.toList(), customerVisible)

    fun setNote(note: Note) {
        identifier = note.identifier
        author = note.author
        message = note.message
        customerVisible = note.customerVisible

        changeImages(note.images)

        markClean()
        isNew = false
    }

    fun changeIdentifier(newId: Uuid) {
        identifier = newId
    }

    fun changeImages(newImages: List<StoredImage>) {
        if (_images.toList() != newImages) {
            _images.clear()
            _images.addAll(newImages)
        }
    }

    fun addImage(newImage: StoredImage) {
        if (_images.none { it.identifier == newImage.identifier }) _images.add(newImage)
    }

    fun deleteImage(identifier: Uuid) {
        _images.removeAll { it.identifier == identifier }
    }

    fun changeAuthor(newAuthor: String) {
        author = newAuthor
    }

    fun changeMessage(newMessage: String) {
        message = newMessage
    }

    fun changeCustomerVisible(visible: Boolean) {
        customerVisible = visible
    }

    fun clear() {
        identifier = Uuid.generateV7()
        author = ""
        message = ""
        customerVisible = false
        _images.clear()

        isNew = true
        markClean()
    }

    /** Takes the note as it is now as the saved state */
    fun markClean() {
        saved = snapshot()
    }

    /** Fields that differ from when the note was set */
    fun getChangedFields(): List<ChangedField> {
        val now = snapshot()
        return now.keys.filter { now[it] != saved[it] }
    }

    override fun toString(): String = "NoteSeed($author, $time: $message)"
}
