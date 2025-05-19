package screens.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ticketman.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.eye
import ticketman.composeapp.generated.resources.eye_off
import ticketman.composeapp.generated.resources.lock
import ticketman.composeapp.generated.resources.password_string
import ticketman.composeapp.generated.resources.user
import ticketman.composeapp.generated.resources.username_string

/**
 * Login screen composable
 */

// TODO: Implement logic for login

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier
) {
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
}