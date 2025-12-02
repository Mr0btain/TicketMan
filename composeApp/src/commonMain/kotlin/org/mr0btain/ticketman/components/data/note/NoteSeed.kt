package org.mr0btain.ticketman.components.data.note

import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

class NoteSeed {
    private var _identifier: UUID = UUIDv7.randomUUID()
    val identifier: UUID get() = _identifier

    private var _images: MutableList<StoredImage> = mutableListOf()
    val images: List<StoredImage> get() = _images

    private var _isNew: Boolean = true
    val isNew: Boolean get() = _isNew

    private var _time: Long = 0
    val time: Long get() = _time

    private var _author: String = ""
    val author: String get() = _author

    private var _message: String = ""
    val message: String get() = _message

    // List to store the names of fields that have changed
    private val changedFields = mutableListOf<ChangedField>()

    // To check if the object has changed
    val isDirty: Boolean get() = changedFields.isNotEmpty()

    // snapshot of the last saved state
    private var savedState: State = State()

    fun setIdentifier(newId: UUID) {
        if (_identifier != newId) {
            _identifier = newId
            changedFields.add(ChangedField.IDENTIFIER)
        }
    }

    fun setImages(newImages: List<StoredImage>) {
        if (_images != newImages) {
            _images = newImages.toMutableList()
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
        if (_author != newAuthor) {
            _author = newAuthor
            changedFields.add(ChangedField.AUTHOR)
        }
    }

    fun setMessage(newMessage: String) {
        if (_message != newMessage) {
            _message = newMessage
            changedFields.add(ChangedField.MESSAGE)
        }
    }

    fun setTime(newTime: Long) {
        if (_time != newTime) {
            _time = newTime
            changedFields.add(ChangedField.TIME)
        }
    }

    fun clear() {
        _identifier = UUIDv7.randomUUID()
        _author = ""
        _message = ""
        _time = UUIDv7.getLongTime(_identifier)
        _images.clear()

        _isNew = true
        markClean()
    }

    fun markClean() {
        savedState = State(_identifier, _author, _message, _images, _time)
        changedFields.clear()
    }

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