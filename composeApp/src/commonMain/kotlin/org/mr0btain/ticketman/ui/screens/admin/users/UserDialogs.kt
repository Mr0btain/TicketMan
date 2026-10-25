package org.mr0btain.ticketman.ui.screens.admin.users

import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import kotlin.uuid.Uuid
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.PasswordRules
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UsernameRules
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.screens.admin.components.AccessLevelDropDown
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair

/**
 * Add or edit a user. The password field only shows for new users, existing ones use [ResetPasswordDialog]
 * @param user User to edit, a blank one when adding
 * @param isSelf Whether this is the logged in admin, they can't change their own access
 * @param onConfirm Gets the edited user and the password for a new user
 * @param locations Where they could usually work, offered with more than one location
 * @param defaultLocation Their home when they don't have one yet, where the admin works
 */
@Composable
fun UserDialog(
    isVisible: Boolean,
    user: User,
    isNew: Boolean,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (User, String) -> Unit,
    locations: List<Location> = emptyList(),
    defaultLocation: Uuid? = null
) {
    if (!isVisible) return

    // Keyed on the user so opening it for someone else starts fresh
    var username by remember(user) { mutableStateOf(user.username) }
    var name by remember(user) { mutableStateOf(user.name) }
    var email by remember(user) { mutableStateOf(user.email) }
    var phone by remember(user) { mutableStateOf(user.phone) }
    var accessLevel by remember(user) {
        mutableStateOf(if (user.accessLevel == AccessLevel.NONE && isNew) AccessLevel.RESTRICTED else user.accessLevel)
    }
    var password by remember(user) { mutableStateOf("") }
    var confirmPassword by remember(user) { mutableStateOf("") }
    var homeLocation by remember(user) {
        mutableStateOf(user.homeLocation ?: (if (isNew) defaultLocation else null) ?: locations.firstOrNull { it.main }?.identifier)
    }

    val error = when {
        // Older usernames from before the rules can stay as they are
        (isNew || username != user.username) && UsernameRules.problem(username) != null -> UsernameRules.problem(username)
        isNew && PasswordRules.problem(password) != null -> PasswordRules.problem(password)
        isNew && password != confirmPassword -> "Passwords don't match"
        else -> null
    }

    TmDialog(
        title = if (isNew) "New user" else "Edit ${user.username}",
        onDismissRequest = onDismiss,
        maxWidth = 560.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = if (isNew) "Add user" else "Save user",
                confirmEnabled = error == null,
                onCancel = onDismiss,
                onConfirm = {
                    onConfirm(
                        user.copy(
                            username = username,
                            name = name.trim(),
                            email = email.trim(),
                            phone = phone.trim(),
                            accessLevel = accessLevel,
                            homeLocation = homeLocation
                        ),
                        password
                    )
                }
            )
        }
    ) {
        TmFieldPair(
            { TmTextField(modifier = it, value = username, onValueChange = { v -> username = v.trim() }, label = "Username") },
            {
                AccessLevelDropDown(
                    modifier = it,
                    accessLevel = accessLevel,
                    enabled = !isSelf,
                    onSelect = { level -> accessLevel = level }
                )
            }
        )
        TmTextField(modifier = Modifier.fillMaxWidth(), value = name, onValueChange = { name = it }, label = "Name")
        TmFieldPair(
            { TmTextField(modifier = it, value = email, onValueChange = { v -> email = v }, label = "Email") },
            { TmTextField(modifier = it, value = phone, onValueChange = { v -> phone = v }, label = "Phone") }
        )

        // Where their lists start, they can still work at the others
        if (locations.size > 1) {
            TmDropdown(
                modifier = Modifier.fillMaxWidth(),
                label = "Home location",
                options = locations,
                selected = locations.firstOrNull { it.identifier == homeLocation },
                onSelect = { homeLocation = it.identifier },
                optionLabel = { it.label },
                supportingText = "Where their lists start. They can work at the others too"
            )
        }

        if (isSelf) TmNotice(text = "You can't change your own access.")

        if (isNew) {
            TmPasswordField(value = password, onValueChange = { password = it }, showStrength = true)
            TmPasswordField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm password",
                error = if (confirmPassword.isNotEmpty() && confirmPassword != password) "Passwords don't match" else null
            )
        }

        if (error != null && (username.isNotEmpty() || password.isNotEmpty())) {
            TmNotice(text = error, kind = TmNoticeKind.WARNING)
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
        PasswordRules.problem(password) != null -> PasswordRules.problem(password)
        password != confirmPassword -> "Passwords don't match"
        else -> null
    }

    TmDialog(
        title = "Reset ${user.username}'s password",
        description = "They'll be logged out and need the new password to log back in.",
        onDismissRequest = onDismiss,
        maxWidth = 440.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = "Reset password",
                confirmEnabled = error == null,
                onCancel = onDismiss,
                onConfirm = { onConfirm(password) }
            )
        }
    ) {
        TmPasswordField(value = password, onValueChange = { password = it }, label = "New password", showStrength = true)
        TmPasswordField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm password",
            error = if (confirmPassword.isNotEmpty() && confirmPassword != password) "Passwords don't match" else null
        )
    }
}
