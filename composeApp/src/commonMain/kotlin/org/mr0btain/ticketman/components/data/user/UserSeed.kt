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
        get() = _name.firstOrNull() ?: ""
    val lastName: String
        get() = if (_name.size > 1) _name.last() else ""

    /** Seed as a user, the password is blank unless one was set on a new user */
    val user: User
        get() = User(identifier, username, password, joinName(_name), email, phone, accessLevel)

    fun changeUsername(new: String) {
        username = new
    }

    fun changePassword(new: String) {
        password = new
    }

    fun changeName(new: List<String>) {
        if (new.isNotEmpty() && new != _name.toList()) {
            _name.clear()
            _name.addAll(new)
        }
    }

    fun changeEmail(new: String) {
        email = new
    }

    fun changePhone(new: String) {
        phone = new
    }

    fun changeAccessLevel(new: AccessLevel) {
        accessLevel = new
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

    override fun fields() = mapOf(
        ChangedField.USERNAME to username,
        ChangedField.PASSWORD to password,
        ChangedField.NAME to _name.toList(),
        ChangedField.EMAIL to email,
        ChangedField.PHONE to phone,
        ChangedField.ACCESS_LEVEL to accessLevel
    )

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

    override fun toString(): String {
        return "User($identifier, $username, ${name.joinToString(" ")}, $email, $phone, $accessLevel)"
    }
}