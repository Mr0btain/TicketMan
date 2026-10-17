package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField

/**
 * Dialog for adding a sub item to a main item like a Ticket or Customer
 * @param subItemType The type of item were looking to add
 * @param onConfirm What to do when we've confirmed we want to add the item
 * @param onDismissRequest What to do when we want to close the dialog
 */
@Composable
fun AddBasicSubItemDialog(
    subItemType: DataType,
    isVisible: Boolean,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!isVisible) return

    var input by remember { mutableStateOf("") }
    val label = subItemType.stringName

    // Basic checks so obvious typos get caught
    val problem = when {
        input.isBlank() -> null
        subItemType == DataType.EMAIL && !(input.contains("@") && input.contains(".")) -> "Emails look like name@example.com"
        subItemType == DataType.PHONE && (input.length != 10 || !input.all { it.isDigit() }) -> "Phone numbers are 10 digits"
        else -> null
    }

    TmDialog(
        title = "Add ${label.lowercase()}",
        onDismissRequest = onDismissRequest,
        actions = {
            TmButtonRow(
                confirmText = "Add ${label.lowercase()}",
                confirmEnabled = problem == null && input.isNotBlank(),
                onCancel = onDismissRequest,
                onConfirm = {
                    onConfirm(input)
                    onDismissRequest()
                }
            )
        }
    ) {
        TmTextField(
            value = input,
            onValueChange = { input = it },
            label = label,
            error = problem,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (subItemType) {
                    DataType.EMAIL -> KeyboardType.Email
                    DataType.PHONE -> KeyboardType.Phone
                    else -> KeyboardType.Text
                },
                imeAction = ImeAction.Done
            )
        )
    }
}
