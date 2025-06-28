package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.*

@Composable
fun LoginField(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val userName = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    val showPassword = remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
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

@Composable
private fun LoginTextField(
    modifier: Modifier = Modifier,
    username: String,
    onUsernameChange: (text: String) -> Unit = {},
    password: String,
    onPasswordChange: (text: String) -> Unit = {},
    showPassword: Boolean,
    showPasswordOnClick: () -> Unit = {}
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
}