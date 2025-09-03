package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.ui.composables.loading.CircularLoading
import org.mr0btain.ticketman.ui.screens.login.components.LoginDialogState
import org.mr0btain.ticketman.ui.screens.login.components.LoginField
import org.mr0btain.ticketman.ui.screens.login.components.ServerAddressField
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel


@Composable
fun LoginPopup(
    modifier: Modifier,
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope
) {
    var state by remember { mutableStateOf(LoginDialogState.Login) }
    var labelState by remember { mutableStateOf("Server Address") }
    var error by remember { mutableStateOf(false) }
    val effect = remember { 0 }

    // We only ever want this to run once
    // Check connection to the server using stored value, if its good no need to reenter it
    LaunchedEffect(effect) {
        /*
        if (viewModel.dataHandler.checkServerConnection()) {
            state = LoginDialogState.Login
        }
         */
    }

    LoginDialog(
        modifier = modifier,
        state = state,
        labelState = labelState,
        error = error,
        onLoginConfirm = { user, pass ->
            // Set state to loading
            labelState = "Loading"
            state = LoginDialogState.Loading

            coroutineScope.launch {
                /*
                // Try to log in to server
                val result = viewModel.dataHandler.loginToServer(user, pass)

                // If we've successfully logged in then call dismiss and move on
                if (result) {
                    onDismiss()
                } else {
                    error = true
                    labelState = "Login"
                    state = LoginDialogState.Login
                }
                 */
            }
        },
        onAddressConfirm = { address ->
            // Trigger loading screen
            labelState = "Loading"
            state = LoginDialogState.Loading

            coroutineScope.launch {
                // Try to hit server
                /*
                val result = viewModel.dataHandler.checkNewServerConnection(address)

                // Handle result
                if (result) {
                    error = false
                    labelState = "Login"
                    state = LoginDialogState.Login
                } else {
                    error = true
                    labelState = "Server Address"
                    state = LoginDialogState.Address
                }
                 */
            }
        },
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LoginDialog(
    modifier: Modifier = Modifier,
    state: LoginDialogState,
    labelState: String,
    error: Boolean,
    onAddressConfirm: (String) -> Unit,
    onLoginConfirm: (user: String, pass: String) -> Unit,
    onDismiss: () -> Unit
) {
    val padding = 48.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties()
    ) {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(3000)
                    ) togetherWith fadeOut(animationSpec = tween(3000))
                },
                label = labelState
            ) { targetState ->
                when (targetState) {
                    LoginDialogState.Address -> {
                        ServerAddressField(
                            modifier = Modifier.padding(padding),
                            onContinue = { address -> onAddressConfirm(address) },
                            error = error
                        )
                    }

                    LoginDialogState.Login -> {
                        LoginField(
                            modifier = Modifier.padding(padding),
                            error = error,
                            onConfirm = { user, pass -> onLoginConfirm(user, pass) },
                            onDismiss = {
                                // Do nothing as we need to log in
                            }
                        )
                    }

                    LoginDialogState.Loading -> {
                        CircularLoading(
                            Modifier.padding(padding)
                        )
                    }
                }
            }
        }
    }
}