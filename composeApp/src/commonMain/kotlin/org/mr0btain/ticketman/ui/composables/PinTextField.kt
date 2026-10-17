package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.mr0btain.ticketman.data.user.PinRules

/**
 * Hidden digits only field for a station PIN
 */
@Composable
fun PinTextField(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    isError: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(PinRules.LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}
