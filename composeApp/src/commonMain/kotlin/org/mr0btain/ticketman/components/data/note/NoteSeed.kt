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

    // List to store the names of fields that have changed
    private val changedFields = mutableListOf<ChangedField>()

    // To check if the object has changed
    val isDirty: Boolean
        get() = changedFields.isNotEmpty()

    // snapshot of the last saved state
    private var savedState: State = State()

    val note: Note
        get() = Note(identifier, author, message, _images.toList())

    fun setNote(
        note: Note
    ) {
        identifier = note.identifier
        author = note.author
        message = note.message

        changeImages(note.images)

        markClean()
        isNew = false
    }

    fun changeIdentifier(newId: Uuid) {
        if (identifier != newId) {
            identifier = newId
            changedFields.add(ChangedField.IDENTIFIER)
        }
    }

    fun changeImages(newImages: List<StoredImage>) {
        if (_images != newImages) {
            _images.clear()
            _images.addAll(newImages)
            changedFields.add(ChangedField.IMAGE)
        }
    }

    fun addImage(newImage: StoredImage) {
        if (!_images.contains(newImage)) {
            _images.add(newImage)
            changedFields.add(ChangedField.IMAGE)
        }
    }

    fun deleteImage(identifier: Uuid) {
        _images.first { it.identifier == identifier }.also { index ->
            _images.remove(index)
            changedFields.add(ChangedField.IMAGE)
        }
    }

    fun changeAuthor(newAuthor: String) {
        if (author != newAuthor) {
            author = newAuthor
            changedFields.add(ChangedField.AUTHOR)
        }
    }

    fun changeMessage(newMessage: String) {
        if (message != newMessage) {
            message = newMessage
            changedFields.add(ChangedField.MESSAGE)
        }
    }

    fun clear() {
        identifier = Uuid.generateV7()
        author = ""
        message = ""
        _images.clear()

        isNew = true
        markClean()
    }

    fun markClean() {
        savedState = State(identifier, author, message, _images)
        changedFields.clear()
    }

    fun getChangedFields(): List<ChangedField> = changedFields.toList()

    override fun toString(): String = "NoteSeed($author, $time: $message)"

    private data class State(
        val identifier: Uuid = Uuid.generateV7(),
        val author: String = "",
        val message: String = "",
        val images: List<StoredImage> = emptyList()
    )
}