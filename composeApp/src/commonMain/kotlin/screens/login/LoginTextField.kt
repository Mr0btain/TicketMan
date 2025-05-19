package screens.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.*


@Composable
fun LoginTextField(
    username: String,
    onUsernameChange: (text: String) -> Unit = {},
    password: String,
    onPasswordChange: (text: String) -> Unit = {},
    showPassword: Boolean,
    showPasswordOnClick: () -> Unit = {}
) {
    // Username field
    OutlinedTextField(
        label = {
            Text(stringResource(Res.string.username_string))
        },
        value = username,
        onValueChange = onUsernameChange,
        singleLine = true,
        leadingIcon = {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(Res.drawable.user),
                contentDescription = "Username"
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        colors = OutlinedTextFieldDefaults.colors(

        )
    )

    Spacer(Modifier.padding(6.dp))

    // Password field
    OutlinedTextField(
        label = {
            Text(stringResource(Res.string.password_string))
        },
        value = password,
        onValueChange = onPasswordChange,
        singleLine = true,
        leadingIcon = {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(Res.drawable.lock),
                contentDescription = "Password"
            )
        },
        trailingIcon = {
            IconButton(
                onClick = showPasswordOnClick,
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    painter = if (showPassword) painterResource(Res.drawable.eye) else painterResource(Res.drawable.eye_off),
                    contentDescription = "Show Password"
                )
            }
        },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation('*'),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        )
    )
}