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
import screens.settings.components.SettingsViewModel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.login_button_text
import ticketman.composeapp.generated.resources.login_text
import ui.theme.AppTheme

/**
 * Login screen composable made for dialog but can be used either way
 */

// TODO: Implement logic for login

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onConfirm: (user:String, pass: String) -> Boolean,
    onDismiss: () -> Unit,
    isDarkMode: Boolean
) {
    val userName = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val showPassword = remember { mutableStateOf(false) }

    Surface(
        color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer,
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
                onClick = {
                    // Check if its valid
                    onConfirm(userName.value, password.value)

                    // Dismiss
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(Res.string.login_button_text),
                    fontSize = 18.sp
                )
            }
        }
    }
}