package org.mr0btain.ticketman.ui.screens.admin.users

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.components.utils.splitName
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.composables.ConfirmCancelButtons
import org.mr0btain.ticketman.ui.composables.DialogTextRow
import org.mr0btain.ticketman.ui.screens.admin.components.AccessLevelDropDown
import org.mr0btain.ticketman.ui.screens.login.components.PasswordTextField

/**
 * Add or edit a user. The password field only shows for new users, existing ones use [ResetPasswordDialog]
 * @param user User to edit, a blank one when adding
 * @param isSelf Whether this is the logged in admin, they can't change their own access
 * @param onConfirm Gets the edited user and the password for a new user
 */
@Composable
fun UserDialog(
    isVisible: Boolean,
    user: User,
    isNew: Boolean,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (User, String) -> Unit
) {
    if (!isVisible) return

    // Keyed on the user so opening it for someone else starts fresh
    var username by remember(user) { mutableStateOf(user.username) }
    var firstName by remember(user) { mutableStateOf(splitName(user.name).first()) }
    var lastName by remember(user) { mutableStateOf(splitName(user.name).drop(1).joinToString(" ")) }
    var email by remember(user) { mutableStateOf(user.email) }
    var phone by remember(user) { mutableStateOf(user.phone) }
    var accessLevel by remember(user) {
        mutableStateOf(if (user.accessLevel == AccessLevel.NONE && isNew) AccessLevel.RESTRICTED else user.accessLevel)
    }
    var password by remember(user) { mutableStateOf("") }
    var confirmPassword by remember(user) { mutableStateOf("") }

    val error = when {
        username.isBlank() -> "Username is required"
        username.any { it.isWhitespace() } -> "Username can't have spaces"
        isNew && password.isBlank() -> "Password is required"
        isNew && password != confirmPassword -> "Passwords don't match"
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(4.dp),
                    text = if (isNew) "Add User" else "Edit User: ${user.username}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    maxLines = 1
                )

                HorizontalDivider()
                Spacer(Modifier.padding(8.dp))

                DialogTextRow(
                    firstLabel = "Username",
                    firstValue = username,
                    onFirstValueChange = { username = it.trim() },
                    secondLabel = "Email",
                    secondValue = email,
                    onSecondValueChange = { email = it }
                )

                DialogTextRow(
                    firstLabel = "First Name",
                    firstValue = firstName,
                    onFirstValueChange = { firstName = it },
                    secondLabel = "Last Name",
                    secondValue = lastName,
                    onSecondValueChange = { lastName = it }
                )

                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(0.5f),
                        label = { Text("Phone") },
                        value = phone,
                        onValueChange = { phone = it },
                        singleLine = true
                    )

                    Spacer(Modifier.padding(8.dp))

                    AccessLevelDropDown(
                        modifier = Modifier.weight(0.5f),
                        accessLevel = accessLevel,
                        enabled = !isSelf,
                        onSelect = { accessLevel = it }
                    )
                }

                if (isSelf) {
                    Text(
                        text = "You can't change your own access",
                        fontSize = 12.sp
                    )
                }

                if (isNew) {
                    PasswordTextField(
                        modifier = Modifier.padding(8.dp),
                        text = password,
                        labelText = "Password",
                        validateStrengthPassword = true,
                        onTextChanged = { password = it }
                    )

                    PasswordTextField(
                        modifier = Modifier.padding(8.dp),
                        text = confirmPassword,
                        labelText = "Confirm Password",
                        hasError = confirmPassword.isNotEmpty() && confirmPassword != password,
                        onTextChanged = { confirmPassword = it }
                    )
                }

                Spacer(Modifier.padding(8.dp))

                if (error != null) {
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(Modifier.padding(4.dp))
                }

                ConfirmCancelButtons(
                    onCancelClick = onDismiss,
                    onConfirmClick = {
                        if (error == null) {
                            val name = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }

                            onConfirm(
                                user.copy(
                                    username = username,
                                    name = joinName(name),
                                    email = email.trim(),
                                    phone = phone.trim(),
                                    accessLevel = accessLevel
                                ),
                                password
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * Sets a new password for a user. Their session is ended so they have to log in with it
 */
@Composable
fun ResetPasswordDialog(
    isVisible: Boolean,
    user: User,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!isVisible) return

    var password by remember(user) { mutableStateOf("") }
    var confirmPassword by remember(user) { mutableStateOf("") }

    val error = when {
        password.isBlank() -> "Password is required"
        password != confirmPassword -> "Passwords don't match"
        else -> null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(4.dp),
                    text = "Reset Password: ${user.username}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    maxLines = 1
                )

                HorizontalDivider()
                Spacer(Modifier.padding(8.dp))

                PasswordTextField(
                    modifier = Modifier.padding(8.dp),
                    text = password,
                    labelText = "New Password",
                    validateStrengthPassword = true,
                    onTextChanged = { password = it }
                )

                PasswordTextField(
                    modifier = Modifier.padding(8.dp),
                    text = confirmPassword,
                    labelText = "Confirm Password",
                    hasError = confirmPassword.isNotEmpty() && confirmPassword != password,
                    onTextChanged = { confirmPassword = it }
                )

                Text(
                    text = "They'll be logged out and need the new password to log back in.",
                    fontSize = 12.sp
                )

                Spacer(Modifier.padding(8.dp))

                if (error != null && confirmPassword.isNotEmpty()) {
                    Text(text = error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.padding(4.dp))
                }

                ConfirmCancelButtons(
                    onCancelClick = onDismiss,
                    onConfirmClick = { if (error == null) onConfirm(password) }
                )
            }
        }
    }
}
