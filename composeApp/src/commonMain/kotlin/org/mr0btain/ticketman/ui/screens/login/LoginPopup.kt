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
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.login.components.RequestAccountForm
import org.mr0btain.ticketman.ui.screens.login.components.ServerAddressField
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel


@Composable
fun LoginPopup(
    modifier: Modifier,
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    coroutineScope: CoroutineScope
) {
    val loginState by viewModel.loginState.collectAsState()
    val serverConnected by viewModel.serverConnected.collectAsState()
    val loginMessage by viewModel.loginMessage.collectAsState()

    // Swaps the login form for the account request form
    var requestingAccount by remember { mutableStateOf(false) }

    // Whether we've tried an address in this popup, so we only flag errors after an attempt
    var addressAttempted by remember { mutableStateOf(false) }

    // Ask for the address until we can reach a server, then ask for credentials
    val state = when {
        loginState == LoginState.LOADING -> LoginDialogState.Loading
        !serverConnected -> LoginDialogState.Address
        requestingAccount -> LoginDialogState.Request
        else -> LoginDialogState.Login
    }

    val error = when (state) {
        LoginDialogState.Address -> addressAttempted
        LoginDialogState.Login -> loginState == LoginState.FAILED
        LoginDialogState.Loading, LoginDialogState.Request -> false
    }

    LoginDialog(
        modifier = modifier,
        state = state,
        labelState = state.name,
        error = error,
        initialUsername = viewModel.savedUsername,
        message = loginMessage,
        onLoginConfirm = { user, pass -> viewModel.login(user, pass) },
        onRequestAccount = { requestingAccount = true },
        onSubmitRequest = { request, onResult -> viewModel.requestAccount(request, onResult) },
        onBackFromRequest = { requestingAccount = false },
        onAddressConfirm = { address ->
            addressAttempted = true
            viewModel.setServerUrl(address.trim())
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
    initialUsername: String,
    message: String?,
    onAddressConfirm: (String) -> Unit,
    onLoginConfirm: (user: String, pass: String) -> Unit,
    onRequestAccount: () -> Unit,
    onSubmitRequest: (AccountRequest, (String?) -> Unit) -> Unit,
    onBackFromRequest: () -> Unit,
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
                        animationSpec = tween(300)
                    ) togetherWith fadeOut(animationSpec = tween(300))
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
                            initialUsername = initialUsername,
                            message = message,
                            onRequestAccount = onRequestAccount,
                            onConfirm = { user, pass -> onLoginConfirm(user, pass) }
                        )
                    }

                    LoginDialogState.Request -> {
                        RequestAccountForm(
                            modifier = Modifier.padding(32.dp),
                            onSubmit = onSubmitRequest,
                            onBack = onBackFromRequest
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