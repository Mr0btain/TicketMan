package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmLoading
import org.mr0btain.ticketman.ui.screens.login.components.LoginDialogState
import org.mr0btain.ticketman.ui.screens.login.components.LoginField
import org.mr0btain.ticketman.ui.screens.login.components.LoginState
import org.mr0btain.ticketman.ui.screens.login.components.RequestAccountForm
import org.mr0btain.ticketman.ui.screens.login.components.ServerAddressField
import org.mr0btain.ticketman.ui.screens.dashboard.components.MainViewModel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.login_text

@Composable
fun LoginPopup(
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    val loginState by viewModel.session.loginState.collectAsState()
    val serverConnected by viewModel.session.serverConnected.collectAsState()
    val loginMessage by viewModel.session.loginMessage.collectAsState()

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
        state = state,
        error = error,
        initialUsername = viewModel.session.savedUsername,
        message = loginMessage,
        onLoginConfirm = { user, pass -> viewModel.session.login(user, pass) },
        onRequestAccount = { requestingAccount = true },
        onSubmitRequest = { request, onResult -> viewModel.session.requestAccount(request, onResult) },
        onBackFromRequest = { requestingAccount = false },
        onAddressConfirm = { address ->
            addressAttempted = true
            viewModel.settings.setServerUrl(address.trim())
        },
        onDismiss = onDismiss
    )
}

@Composable
private fun LoginDialog(
    state: LoginDialogState,
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
    TmDialog(
        title = when (state) {
            LoginDialogState.Address -> "Connect to the shop's server"
            LoginDialogState.Login -> stringResource(Res.string.login_text)
            LoginDialogState.Request -> "Request an account"
            LoginDialogState.Loading -> "Logging in"
        },
        description = when (state) {
            LoginDialogState.Address -> "Type the address your admin gave you."
            LoginDialogState.Login -> "New and have a setup code? Enter it as your password."
            LoginDialogState.Request -> "An admin looks it over. Once approved, log in with this username and password to finish setting up."
            LoginDialogState.Loading -> null
        },
        onDismissRequest = onDismiss,
        maxWidth = 460.dp
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "Login step"
        ) { targetState ->
            when (targetState) {
                LoginDialogState.Address -> ServerAddressField(onContinue = onAddressConfirm, error = error)

                LoginDialogState.Login -> LoginField(
                    error = error,
                    initialUsername = initialUsername,
                    message = message,
                    onRequestAccount = onRequestAccount,
                    onConfirm = onLoginConfirm
                )

                LoginDialogState.Request -> RequestAccountForm(onSubmit = onSubmitRequest, onBack = onBackFromRequest)

                LoginDialogState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TmLoading() }
            }
        }
    }
}
