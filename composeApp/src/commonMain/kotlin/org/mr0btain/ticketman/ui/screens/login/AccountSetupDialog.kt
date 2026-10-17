package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.components.utils.splitName
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.ui.screens.login.components.AccountSetup
import org.mr0btain.ticketman.ui.screens.login.components.MIN_PASSWORD_LENGTH
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.inputs.TmPinField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair

/**
 * First login setup. Can't be clicked away, only finished or cancelled
 * @param onFinish Sends the details, calls back with null when done or what went wrong
 */
@Composable
fun AccountSetupDialog(
    setup: AccountSetup,
    onFinish: (name: String, email: String, phone: String, password: String, pin: String, onResult: (String?) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    val existing = (setup as? AccountSetup.Approved)?.user
    val isInvite = setup is AccountSetup.Invite
    val username = when (setup) {
        is AccountSetup.Approved -> setup.user.username
        is AccountSetup.Invite -> setup.username
    }

    val existingName = existing?.name?.let { splitName(it) }.orEmpty()
    var firstName by remember(setup) { mutableStateOf(existingName.firstOrNull().orEmpty().replaceFirstChar { it.titlecase() }) }
    var lastName by remember(setup) { mutableStateOf(existingName.drop(1).joinToString(" ").replaceFirstChar { it.titlecase() }) }
    var email by remember(setup) { mutableStateOf(existing?.email.orEmpty()) }
    var phone by remember(setup) { mutableStateOf(existing?.phone.orEmpty()) }
    var password by remember(setup) { mutableStateOf("") }
    var confirmPassword by remember(setup) { mutableStateOf("") }
    var pin by remember(setup) { mutableStateOf("") }
    var confirmPin by remember(setup) { mutableStateOf("") }

    var submitting by remember(setup) { mutableStateOf(false) }
    var serverError by remember(setup) { mutableStateOf<String?>(null) }

    // Invites have to pick a password, approved requests already chose one
    val problem = when {
        firstName.isBlank() -> "Your name is required"
        (isInvite || password.isNotEmpty()) && password.length < MIN_PASSWORD_LENGTH -> "Password needs at least $MIN_PASSWORD_LENGTH characters"
        password != confirmPassword -> "Passwords don't match"
        PinRules.problem(pin) != null -> PinRules.problem(pin)
        pin != confirmPin -> "PINs don't match"
        else -> null
    }

    TmDialog(
        title = "Welcome, $username",
        description = if (isInvite) "Finish setting up your account by choosing a password and checking your details."
            else "Your account was approved. Check your details to finish setting up.",
        // Can't be clicked away, only finished or cancelled
        onDismissRequest = {},
        dismissOnOutsideClick = false,
        maxWidth = 500.dp,
        actions = {
            TmButtonRow(
                cancelText = if (isInvite) "Cancel" else "Log out",
                confirmText = "Finish setup",
                confirmEnabled = problem == null,
                loading = submitting,
                onCancel = onCancel,
                onConfirm = {
                    submitting = true
                    serverError = null

                    val name = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }

                    onFinish(joinName(name).lowercase(), email.trim(), phone.trim(), password, pin) { error ->
                        submitting = false
                        serverError = error
                    }
                }
            )
        }
    ) {
        TmFieldPair(
            { TmTextField(modifier = it, value = firstName, onValueChange = { v -> firstName = v }, label = "First name") },
            { TmTextField(modifier = it, value = lastName, onValueChange = { v -> lastName = v }, label = "Last name") }
        )
        TmFieldPair(
            { TmTextField(modifier = it, value = email, onValueChange = { v -> email = v }, label = "Email") },
            { TmTextField(modifier = it, value = phone, onValueChange = { v -> phone = v }, label = "Phone") }
        )

        TmPasswordField(
            value = password,
            onValueChange = { password = it },
            label = if (isInvite) "Password" else "New password (optional)",
            showStrength = true
        )
        TmPasswordField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm password",
            error = if (confirmPassword.isNotEmpty() && confirmPassword != password) "Passwords don't match" else null
        )

        TmFieldPair(
            { TmPinField(modifier = it, value = pin, onValueChange = { v -> pin = v }, label = "PIN for the counter") },
            {
                TmPinField(
                    modifier = it,
                    value = confirmPin,
                    onValueChange = { v -> confirmPin = v },
                    label = "Confirm PIN",
                    error = if (confirmPin.length == PinRules.LENGTH && confirmPin != pin) "PINs don't match" else null
                )
            }
        )

        (serverError ?: problem)?.let { message ->
            TmNotice(text = message, kind = if (serverError != null) TmNoticeKind.ERROR else TmNoticeKind.WARNING)
        }
    }
}
