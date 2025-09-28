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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.ui.composables.ConfirmOrCancelButtonRow

/**
 * Text edit dialog
 */

@Composable
fun TextEditDialog(
    label: String,
    value: String,
    visible: Boolean,
    onConfirm: (String) -> Boolean,
    onCancel: () -> Unit,
    keyboardType: KeyboardType
) {
    val focusRequester = remember { FocusRequester() }
    var isError by remember { mutableStateOf(false) }

    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text =value,
                selection = TextRange(value.length)
            )
        )
    }

    if (visible) {
        Dialog(
            onDismissRequest = onCancel
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
                    OutlinedTextField(
                        modifier = Modifier.focusRequester(focusRequester),
                        label = { Text(label) },
                        singleLine = true,
                        value = textFieldValueState,
                        shape = RoundedCornerShape(8.dp),
                        onValueChange = { textFieldValueState = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = keyboardType,
                            imeAction = ImeAction.Next
                        ),
                        isError = isError
                    )

                    Spacer(Modifier.padding(12.dp))

                    ConfirmOrCancelButtonRow(
                        onConfirmClick = {
                            val result = onConfirm(textFieldValueState.text)

                            if (result) {
                                isError = false
                                onCancel()
                            } else isError = true
                        },
                        onCancelClick = onCancel
                    )
                }
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}