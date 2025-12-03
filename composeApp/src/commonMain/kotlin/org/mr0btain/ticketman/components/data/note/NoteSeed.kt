package org.mr0btain.ticketman.components.data.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.exposed.v1.core.not
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

class NoteSeed {
    var identifier: UUID by mutableStateOf(UUIDv7.randomUUID())
        private set

    private val _images = mutableStateListOf<StoredImage>()
    val images: List<StoredImage> = _images

    var isNew: Boolean by mutableStateOf(true)
        private set

    var time: Long by mutableStateOf(0)
        private set

    var author by mutableStateOf("")
        private set

    var message: String by mutableStateOf("")
        private set

    // List to store the names of fields that have changed
    private val changedFields = mutableListOf<ChangedField>()

    // To check if the object has changed
    val isDirty: Boolean get() = changedFields.isNotEmpty()

    // snapshot of the last saved state
    private var savedState: State = State()

    fun setNote(
        note: Note,
        imageList: List<StoredImage>
    ) {
        identifier = note.identifier
        author = note.author
        message = note.message
        time = UUIDv7.getLongTime(identifier)

        setImages(imageList)

        markClean()
        isNew = false
    }

    fun setIdentifier(newId: UUID) {
        if (identifier != newId) {
            identifier = newId
            changedFields.add(ChangedField.IDENTIFIER)
        }
    }

    fun setImages(newImages: List<StoredImage>) {
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

    fun removeImage(identifier: UUID) {
        if (_images.removeIf { it.identifier == identifier }) {
            changedFields.add(ChangedField.IMAGE)
        }
    }

    fun setAuthor(newAuthor: String) {
        if (author != newAuthor) {
            author = newAuthor
            changedFields.add(ChangedField.AUTHOR)
        }
    }

    fun setMessage(newMessage: String) {
        if (message != newMessage) {
            message = newMessage
            changedFields.add(ChangedField.MESSAGE)
        }
    }

    fun setTime(newTime: Long) {
        if (time != newTime) {
            time = newTime
            changedFields.add(ChangedField.TIME)
        }
    }

    fun clear() {
        identifier = UUIDv7.randomUUID()
        author = ""
        message = ""
        time = UUIDv7.getLongTime(identifier)
        _images.clear()

        isNew = true
        markClean()
    }

    fun markClean() {
        savedState = State(identifier, author, message, _images, time)
        changedFields.clear()
    }

    fun getChangedFields(): List<ChangedField> = changedFields.toList()

    override fun toString(): String {
        TODO("Not yet implemented")
    }

    private data class State(
        val identifier: UUID = UUIDv7.randomUUID(),
        val author: String = "",
        val message: String = "",
        val images: List<StoredImage> = emptyList(),
        val time: Long = 0
    )
}