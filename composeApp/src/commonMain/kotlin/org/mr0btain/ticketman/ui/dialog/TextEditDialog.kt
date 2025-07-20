package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
    isError: Boolean,
    inputFilter: (String) -> String,
    keyboardType: KeyboardType,
    buttonText: String,
    placeholderText: String = ""
) {
    val focusRequester = remember { FocusRequester() }

    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text =value,
                selection = TextRange(value.length)
            )
        )
    }

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
                modifier = Modifier.focusRequester(focusRequester),
                singleLine = true,
                value = textFieldValueState,
                shape = RoundedCornerShape(8.dp),
                onValueChange = {
                    // Filter input
                    val filteredText = inputFilter(it.text)

                    textFieldValueState = it.copy(text = filteredText)
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
                    onConfirm(textFieldValueState.text)
                    // disable / enable the button
                }
            ) {
                Text(buttonText)
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}