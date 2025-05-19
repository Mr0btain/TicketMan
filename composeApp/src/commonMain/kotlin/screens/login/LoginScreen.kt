package screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.MainViewModel
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.login_button_text
import ticketman.composeapp.generated.resources.login_text
import ui.theme.AppTheme

/**
 * Login screen composable
 */

// TODO: Implement logic for login

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    val userName = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val showPassword = remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
            color = if (viewModel.darkMode.value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(Res.string.login_text),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.padding(12.dp))

                LoginTextField(
                    username = userName.value,
                    onUsernameChange = { userName.value = it },
                    password = password.value,
                    onPasswordChange = { password.value = it },
                    showPassword = showPassword.value,
                    showPasswordOnClick = { showPassword.value = !showPassword.value }
                )

                Spacer(Modifier.padding(12.dp))

                Button(
                    onClick = {}
                ) {
                    Text(
                        text = stringResource(Res.string.login_button_text),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

/*
val password = remember { mutableStateOf("") }
    val strongPass = remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        PasswordTextField(
            text = password.value,
            labelText = "Password",
            validateStrengthPassword = true,
            onTextChanged = { password.value = it },
            hasError = if (password.value.isNotBlank()) !strongPass.value else false,
            onHasStrongPassword = { strongPass.value = it }
        )
    }
 */