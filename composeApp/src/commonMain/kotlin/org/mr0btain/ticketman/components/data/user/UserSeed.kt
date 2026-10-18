package org.mr0btain.ticketman.components.data.user

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mr0btain.ticketman.components.data.DataSeed
import org.mr0btain.ticketman.components.data.operations.ChangedField
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.components.utils.splitName
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.User
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserSeed: DataSeed("UserSeed") {
    var username: String by mutableStateOf("")
        private set
    var password: String by mutableStateOf("")
        private set
    var email: String by mutableStateOf("")
        private set
    var phone: String by mutableStateOf("")
        private set
    var accessLevel: AccessLevel by mutableStateOf(AccessLevel.RESTRICTED)
        private set

    private val _name = mutableStateListOf("")
    val name: List<String>
        get() = _name
    val firstName: String
        get() = _name.first()
    val lastName: String
        get() = _name.last()

    /** Seed as a user, the password is blank unless one was set on a new user */
    val user: User
        get() = User(identifier, username, password, joinName(_name), email, phone, accessLevel)

    fun changeUsername(new: String) {
        if (username != new) {
            username = new
            _changedFields.add(ChangedField.USERNAME)
        }
    }

    fun changePassword(new: String) {
        if (password != new) {
            password = new
            _changedFields.add(ChangedField.PASSWORD)
        }
    }

    fun changeName(new: List<String>) {
        if (new.isNotEmpty() && new != _name.toList()) {
            _name.clear()
            _name.addAll(new)
            _changedFields.add(ChangedField.NAME)
        }
    }

    fun changeEmail(new: String) {
        if (email != new) {
            email = new
            _changedFields.add(ChangedField.EMAIL)
        }
    }

    fun changePhone(new: String) {
        if (phone != new) {
            phone = new
            _changedFields.add(ChangedField.PHONE)
        }
    }

    fun changeAccessLevel(new: AccessLevel) {
        if (accessLevel != new) {
            accessLevel = new
            _changedFields.add(ChangedField.ACCESS_LEVEL)
        }
    }

    fun setUser(user: User, notes: List<Note>) {
        identifier = user.identifier
        username = user.username
        password = user.password // Don't know if we should even keep track of this here
        _name.clear()
        _name.addAll(splitName(user.name))
        email = user.email
        phone = user.phone
        accessLevel = user.accessLevel
        _notes.clear()
        _notes.addAll(notes)

        isNew = false
        markClean()
    }

    override fun clear() {
        identifier = Uuid.generateV7()
        _notes.clear()
        _images.clear()
        username = ""
        password = ""
        _name.clear()
        email = ""
        phone = ""
        accessLevel = AccessLevel.RESTRICTED

        isNew = true
        markClean()
    }

    override fun markClean() {
        savedState = State(identifier, username, password, joinName(name), email, phone, accessLevel).hashCode()
        _changedFields.clear()
    }

    override fun toString(): String {
        return "User($identifier, $username, ${name.joinToString(" ")}, $email, $phone, $accessLevel)"
    }

    private data class State(
        val identifier: Uuid = Uuid.generateV7(),
        val username: String = "",
        val password: String = "",
        val name: String = "",
        val email: String = "",
        val phone: String = "",
        val accessLevel: AccessLevel = AccessLevel.RESTRICTED
    )
}