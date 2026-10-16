package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.ui.composables.CustomSurface
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors

/**
 * Dialog for adding a sub item to a main item like a Ticket or Customer
 * @param modifier Modifier
 * @param subItemType The type of item were looking to add
 * @param onConfirm What to do when we've confirmed we want to add the item
 * @param onDismissRequest What to do when we want to close the dialog
 */

@Composable
fun AddBasicSubItemDialog(
    modifier: Modifier = Modifier,
    subItemType: DataType,
    isVisible: Boolean,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (isVisible) {
        var input by remember { mutableStateOf("") }
        val label = subItemType.stringName
        var isError by remember { mutableStateOf(false) }

        // For basic checks for validity
        val emailOptions = listOf("@", ".")

        Dialog(
            onDismissRequest = { onDismissRequest() },
        ) {
            CustomSurface {
                Column(
                    modifier = Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Add new $label",
                        fontSize = 20.sp
                    )

                    Spacer(Modifier.padding(4.dp))

                    OutlinedTextField(
                        label = { Text(label) },
                        value = input,
                        onValueChange = { value ->
                            when (subItemType) {
                                DataType.EMAIL -> {
                                    emailOptions.forEach { isError = !value.contains(it) }
                                    input = value
                                }
                                DataType.PHONE -> {
                                    isError = if (value.length != 10) {
                                        true
                                    } else if (!value.toList().all { it.isDigit() }) {
                                        true
                                    } else false

                                    input = value
                                }
                                else -> { input = value }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        isError = isError
                    )

                    Spacer(Modifier.padding(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onDismissRequest() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Cancel")
                        }

                        Spacer(Modifier.padding(8.dp))

                        Button(
                            onClick = {
                                onConfirm(input)
                                onDismissRequest()
                            },
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isError && input.isNotBlank()
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}