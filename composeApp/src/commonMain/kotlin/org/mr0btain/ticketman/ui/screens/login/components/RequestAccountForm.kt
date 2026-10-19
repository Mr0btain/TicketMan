package org.mr0btain.ticketman.ui.screens.login.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.user.PasswordRules
import org.mr0btain.ticketman.data.user.UsernameRules
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair

/**
 * Asks for an account. It waits for an admin to approve it
 * @param onSubmit Sends the request, calls back with null when it went through or what went wrong
 */
@Composable
fun RequestAccountForm(
    modifier: Modifier = Modifier,
    onSubmit: (AccountRequest, (String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var submitting by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val problem = UsernameRules.problem(username)
        ?: when {
            name.isBlank() -> "Your name is required"
            PasswordRules.problem(password) != null -> PasswordRules.problem(password)
            password != confirmPassword -> "Passwords don't match"
            else -> null
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)
    ) {
        TmTextField(
            modifier = Modifier.fillMaxWidth(),
            value = username,
            onValueChange = { username = it.trim().lowercase() },
            label = "Username"
        )

        TmTextField(modifier = Modifier.fillMaxWidth(), value = name, onValueChange = { name = it }, label = "Name")

        TmFieldPair(
            { TmTextField(modifier = it, value = email, onValueChange = { v -> email = v }, label = "Email") },
            { TmTextField(modifier = it, value = phone, onValueChange = { v -> phone = v }, label = "Phone") }
        )

        TmPasswordField(value = password, onValueChange = { password = it }, showStrength = true)

        TmPasswordField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = "Confirm password",
            error = if (confirmPassword.isNotEmpty() && confirmPassword != password) "Passwords don't match" else null
        )

        // Only nag once they've started filling it in
        val shownError = serverError ?: problem.takeIf { username.isNotEmpty() || password.isNotEmpty() }
        if (shownError != null) {
            TmNotice(text = shownError, kind = if (serverError != null) TmNoticeKind.ERROR else TmNoticeKind.WARNING)
        }

        TmButtonRow(
            cancelText = "Back",
            confirmText = "Send request",
            confirmEnabled = problem == null,
            loading = submitting,
            onCancel = onBack,
            onConfirm = {
                submitting = true
                serverError = null

                onSubmit(
                    AccountRequest(
                        username = username,
                        password = password,
                        name = name.trim(),
                        email = email.trim(),
                        phone = phone.trim()
                    )
                ) { error ->
                    submitting = false
                    serverError = error
                    if (error == null) onBack()
                }
            }
        )
    }
}
