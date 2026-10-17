package org.mr0btain.ticketman.ui.screens.login.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.stringResource
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmPasswordField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.password_string
import ticketman.composeapp.generated.resources.user
import ticketman.composeapp.generated.resources.username_string

/**
 * Username and password
 * @param message From the server, like why the login failed
 * @param onRequestAccount Shows a button to ask for an account, null hides it
 */
@Composable
fun LoginField(
    error: Boolean,
    initialUsername: String = "",
    message: String? = null,
    onRequestAccount: (() -> Unit)? = null,
    onConfirm: (String, String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val canLogIn = username.isNotBlank() && password.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
        TmTextField(
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            value = username,
            onValueChange = { username = it },
            label = stringResource(Res.string.username_string),
            leadingIcon = Res.drawable.user,
            size = TmControlSize.LARGE,
            error = if (error) "" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )

        TmPasswordField(
            modifier = Modifier.fillMaxWidth(),
            value = password,
            onValueChange = { password = it },
            label = stringResource(Res.string.password_string),
            size = TmControlSize.LARGE,
            error = if (error) "" else null,
            keyboardActions = KeyboardActions(onDone = { if (canLogIn) onConfirm(username, password) })
        )

        if (message != null) {
            TmNotice(text = message, kind = if (error) TmNoticeKind.ERROR else TmNoticeKind.INFO)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onRequestAccount != null) {
                TmButton(text = "Request an account", onClick = onRequestAccount, variant = TmButtonVariant.GHOST)
            }

            Spacer(Modifier.weight(1f))

            TmButton(text = "Log in", onClick = { onConfirm(username, password) }, enabled = canLogIn, size = TmControlSize.LARGE)
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}
