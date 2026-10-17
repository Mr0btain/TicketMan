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
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.server
import ticketman.composeapp.generated.resources.server_address_string

/**
 * Asks for the server's address
 * @param error Marks the address as unreachable, after a try
 */
@Composable
fun ServerAddressField(
    error: Boolean,
    onContinue: (String) -> Unit
) {
    var address by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
        TmTextField(
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            value = address,
            onValueChange = { address = it },
            label = stringResource(Res.string.server_address_string),
            placeholder = "192.168.1.20:8080",
            leadingIcon = Res.drawable.server,
            size = TmControlSize.LARGE,
            error = if (error) "Couldn't reach a server there. Check the address and that the server is running." else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onContinue(address) })
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TmButton(text = "Connect", onClick = { onContinue(address) }, enabled = address.isNotBlank(), size = TmControlSize.LARGE)
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}
