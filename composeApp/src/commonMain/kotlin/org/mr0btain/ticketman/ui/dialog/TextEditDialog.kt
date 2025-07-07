package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text edit dialog
 */

@Composable
fun TextEditDialog(
    name: String,
    value: String,
    onConfirm: (String) -> Unit,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    inputFilter: (String) -> String,
    keyboardType: KeyboardType,
    buttonText: String,
    placeholderText: String = ""
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(32.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                singleLine = true,
                value = value,
                shape = RoundedCornerShape(8.dp),
                onValueChange = {
                    // Filter input
                    val filteredText = inputFilter(it)
                    onValueChange(filteredText)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next
                ),
                placeholder = { Text(placeholderText) },
                isError = isError
            )

            Spacer(Modifier.padding(12.dp))

            Button(
                onClick = {
                    // save and dismiss the dialog
                    onConfirm(value)
                    // disable / enable the button
                }
            ) {
                Text(buttonText)
            }
        }
    }
}