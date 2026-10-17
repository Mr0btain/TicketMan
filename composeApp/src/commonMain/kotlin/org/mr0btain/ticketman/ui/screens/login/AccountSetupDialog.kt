package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mr0btain.ticketman.components.utils.joinName
import org.mr0btain.ticketman.components.utils.splitName
import org.mr0btain.ticketman.data.user.PinRules
import org.mr0btain.ticketman.ui.composables.PinTextField
import org.mr0btain.ticketman.ui.screens.login.components.AccountSetup
import org.mr0btain.ticketman.ui.screens.login.components.MIN_PASSWORD_LENGTH
import org.mr0btain.ticketman.ui.screens.login.components.PasswordTextField

/**
 * First login setup. Can't be clicked away, only finished or cancelled
 * @param onFinish Sends the details, calls back with null when done or what went wrong
 */
@Composable
fun AccountSetupDialog(
    setup: AccountSetup,
    onFinish: (name: String, email: String, phone: String, password: String, pin: String, onResult: (String?) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    val existing = (setup as? AccountSetup.Approved)?.user
    val isInvite = setup is AccountSetup.Invite
    val username = when (setup) {
        is AccountSetup.Approved -> setup.user.username
        is AccountSetup.Invite -> setup.username
    }

    val existingName = existing?.name?.let { splitName(it) }.orEmpty()
    var firstName by remember(setup) { mutableStateOf(existingName.firstOrNull().orEmpty().replaceFirstChar { it.titlecase() }) }
    var lastName by remember(setup) { mutableStateOf(existingName.drop(1).joinToString(" ").replaceFirstChar { it.titlecase() }) }
    var email by remember(setup) { mutableStateOf(existing?.email.orEmpty()) }
    var phone by remember(setup) { mutableStateOf(existing?.phone.orEmpty()) }
    var password by remember(setup) { mutableStateOf("") }
    var confirmPassword by remember(setup) { mutableStateOf("") }
    var pin by remember(setup) { mutableStateOf("") }
    var confirmPin by remember(setup) { mutableStateOf("") }

    var submitting by remember(setup) { mutableStateOf(false) }
    var serverError by remember(setup) { mutableStateOf<String?>(null) }

    // Invites have to pick a password, approved requests already chose one
    val problem = when {
        firstName.isBlank() -> "Your name is required"
        (isInvite || password.isNotEmpty()) && password.length < MIN_PASSWORD_LENGTH -> "Password needs at least $MIN_PASSWORD_LENGTH characters"
        password != confirmPassword -> "Passwords don't match"
        PinRules.problem(pin) != null -> PinRules.problem(pin)
        pin != confirmPin -> "PINs don't match"
        else -> null
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .width(440.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome, $username",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = if (isInvite) "Finish setting up your account by choosing a password and checking your details."
                        else "Your account was approved. Check your details to finish setting up.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
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
                    labelText = if (isInvite) "Password" else "New Password (optional)",
                    validateStrengthPassword = true,
                    onTextChanged = { password = it }
                )

                PasswordTextField(
                    text = confirmPassword,
                    labelText = "Confirm Password",
                    hasError = confirmPassword.isNotEmpty() && confirmPassword != password,
                    onTextChanged = { confirmPassword = it }
                )

                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = "Pick a ${PinRules.LENGTH} digit PIN for signing in at the counter.",
                    fontSize = 13.sp
                )

                Row {
                    PinTextField(
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        value = pin,
                        label = "PIN",
                        onValueChange = { pin = it }
                    )

                    PinTextField(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        value = confirmPin,
                        label = "Confirm PIN",
                        isError = confirmPin.length == PinRules.LENGTH && confirmPin != pin,
                        onValueChange = { confirmPin = it }
                    )
                }

                (serverError ?: problem)?.let { message ->
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = message,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.padding(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onCancel) {
                        Text(if (isInvite) "Cancel" else "Log Out")
                    }

                    Button(
                        enabled = problem == null && !submitting,
                        onClick = {
                            submitting = true
                            serverError = null

                            val name = listOf(firstName.trim(), lastName.trim()).filter { it.isNotEmpty() }

                            onFinish(joinName(name).lowercase(), email.trim(), phone.trim(), password, pin) { error ->
                                submitting = false
                                serverError = error
                            }
                        }
                    ) {
                        Text("Finish Setup")
                    }
                }
            }
        }
    }
}
