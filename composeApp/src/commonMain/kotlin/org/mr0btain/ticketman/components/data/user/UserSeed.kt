package org.mr0btain.ticketman.components.data.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.UUID

class UserSeed: DataSeed() {
    var username: String by mutableStateOf("")
        private set
    var password: String by mutableStateOf("")
        private set
    var name: PersonName by mutableStateOf(PersonName())
       private set
    var email: String by mutableStateOf("")
        private set
    var phone: String by mutableStateOf("")
        private set
    var accessLevel: AccessLevel by mutableStateOf(AccessLevel.RESTRICTED)
        private set

    fun changeUsername(new: String) {
        username = new
        _changedFields.add(ChangedField.USERNAME)
    }

    fun changePassword(new: String) {
        password = new
        _changedFields.add(ChangedField.PASSWORD)
    }

    fun changeName(new: PersonName) {
        name = new
        _changedFields.add(ChangedField.NAME)
    }

    fun changeEmail(new: String) {
        email = new
        _changedFields.add(ChangedField.EMAIL)
    }

    fun changePhone(new: String) {
        phone = new
        _changedFields.add(ChangedField.PHONE)
    }

    fun changeAccessLevel(new: AccessLevel) {
        accessLevel = new
        _changedFields.add(ChangedField.ACCESS_LEVEL)
    }

    fun setUser(user: User, notes: List<Note>) {
        identifier = user.identifier
        username = user.username
        password = user.password // Don't know if we should even keep track of this here
        name = user.name
        email = user.email
        phone = user.phone
        accessLevel = user.accessLevel
        _notes.addAll(notes)
    }

    override fun clear() {
        identifier = UUIDv7.randomUUID()
        _notes.clear()
        _images.clear()
        username = ""
        password = ""
        name = PersonName()
        email = ""
        phone = ""
        accessLevel = AccessLevel.RESTRICTED

        isNew = true
        markClean()
    }

    override fun markClean() {
        savedState = State(identifier, username, password, name, email, phone, accessLevel).hashCode()
        _changedFields.clear()
    }

    override fun toString(): String {
        return "User($identifier, $username, $name, $email, $phone, $accessLevel)"
    }

    private data class State(
        val identifier: UUID = UUIDv7.randomUUID(),
        val username: String = "",
        val password: String = "",
        val name: PersonName = PersonName(),
        val email: String = "",
        val phone: String = "",
        val accessLevel: AccessLevel = AccessLevel.RESTRICTED
    )
}