package org.mr0btain.ticketman.ui.screens.login.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.data.user.UsernameRules
import org.mr0btain.ticketman.server.request.AccountRequest

/** Matches the server's minimum */
const val MIN_PASSWORD_LENGTH = 8

/**
 * Asks for an account. It waits for an admin to approve it
 * @param onSubmit Sends the request, calls back with null when it went through or what went wrong
 */
@Composable
fun RequestAccountForm(
    modifier: Modifier = Modifier,
    onSubmit: (AccountRequest, (String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var submitting by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }

    val problem = UsernameRules.problem(username)
        ?: when {
            firstName.isBlank() -> "Your name is required"
            password.length < MIN_PASSWORD_LENGTH -> "Password needs at least $MIN_PASSWORD_LENGTH characters"
            password != confirmPassword -> "Passwords don't match"
            else -> null
        }

    Column(
        modifier = modifier.width(420.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Request an Account",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            modifier = Modifier.padding(vertical = 8.dp),
            text = "An admin will look it over. Once approved, log in with this username and password to finish setting up.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = username,
            onValueChange = { username = it.trim().lowercase() },
            label = { Text("Username") },
            singleLine = true
        )

        Row {
            OutlinedTextField(
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First Name") },
                singleLine = true
            )

            OutlinedTextField(
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last Name") },
                singleLine = true
            )
        }

        Row {
            OutlinedTextField(
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true
            )

            OutlinedTextField(
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone") },
                singleLine = true
            )
        }

        PasswordTextField(
            modifier = Modifier.padding(top = 8.dp),
            text = password,
            labelText = "Password",
            validateStrengthPassword = true,
            onTextChanged = { password = it }
        )

        PasswordTextField(
            text = confirmPassword,
            labelText = "Confirm Password",
            hasError = confirmPassword.isNotEmpty() && confirmPassword != password,
            onTextChanged = { confirmPassword = it }
        )

        // Only nag once they've started filling it in
        val shownError = serverError ?: problem.takeIf { username.isNotEmpty() || password.isNotEmpty() }

        if (shownError != null) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = shownError,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.padding(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }

            Button(
                enabled = problem == null && !submitting,
                onClick = {
                    submitting = true
                    serverError = null

                    val name = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }

                    onSubmit(
                        AccountRequest(
                            username = username,
                            password = password,
                            name = joinName(name).lowercase(),
                            email = email.trim(),
                            phone = phone.trim()
                        )
                    ) { error ->
                        submitting = false
                        serverError = error
                        if (error == null) onBack()
                    }
                }
            ) {
                Text("Send Request")
            }
        }
    }
}
