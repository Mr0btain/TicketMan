package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.eye
import ticketman.composeapp.generated.resources.eye_off
import ticketman.composeapp.generated.resources.warning_password_level
import ticketman.composeapp.generated.resources.warning_password_level_strong
import ticketman.composeapp.generated.resources.warning_password_level_weak

@Composable
fun PasswordTextField(
    text: String,
    modifier: Modifier = Modifier,
    semanticContentDescription: String = "",
    labelText: String = "",
    validateStrengthPassword: Boolean = false,
    hasError: Boolean = false,
    onHasStrongPassword: (isStrong: Boolean) -> Unit = {},
    onTextChanged: (text: String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val showPassword = remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OutlinedTextField(
            modifier = Modifier
                .semantics { contentDescription = semanticContentDescription },
            value = text,
            onValueChange = onTextChanged,
            placeholder = {
                Text(
                    text = labelText,
                    color = Color.White,
                    fontSize = 16.sp,
                )
            },
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                }
            ),
            singleLine = true,
            isError = hasError,
            visualTransformation = if (showPassword.value) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword.value = !showPassword.value }) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        painter = if (showPassword.value) painterResource(Res.drawable.eye) else painterResource(Res.drawable.eye_off),
                        contentDescription = "Show Password"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (validateStrengthPassword && text.isNotEmpty()) {
            val strengthPasswordType = passwordStrengthCheck(text)

            if (strengthPasswordType == StrengthPasswordTypes.STRONG) {
                onHasStrongPassword(true)
            } else {
                onHasStrongPassword(false)
            }
            Text(
                modifier = Modifier.semantics { contentDescription = "StrengthPasswordMessage" },
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 10.sp
                        )
                    ) {
                        append(stringResource(Res.string.warning_password_level))
                    }


                    when (strengthPasswordType) {
                        StrengthPasswordTypes.STRONG -> {
                            withStyle(
                                style = SpanStyle(
                                    color = Color.Green,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(" ${stringResource(Res.string.warning_password_level_strong)}")
                            }
                        }

                        StrengthPasswordTypes.WEAK -> {
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(" ${stringResource(Res.string.warning_password_level_weak)}")
                            }
                        }
                    }
                }
            )
        }
    }
}