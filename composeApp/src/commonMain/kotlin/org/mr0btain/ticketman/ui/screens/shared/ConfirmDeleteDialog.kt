package org.mr0btain.ticketman.ui.screens.shared

import androidx.compose.runtime.*
import org.mr0btain.ticketman.components.data.operations.DataType
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog

/**
 * Asks before deleting something
 * @param isTwoStep Asks a second time, for things that take a lot with them like a customer's tickets
 */
@Composable
fun ConfirmDeleteDialog(
    dataType: DataType,
    isTwoStep: Boolean = false,
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    if (!isVisible) return

    var confirmCount by remember { mutableIntStateOf(0) }
    val name = dataType.stringName.lowercase()

    TmConfirmDialog(
        title = if (confirmCount == 0) "Delete this $name?" else "Delete it for good?",
        message = if (confirmCount == 0) "This can't be undone."
            else "Everything attached to this $name goes with it, and it can't be brought back.",
        confirmText = if (isTwoStep && confirmCount == 0) "Continue" else "Delete $name",
        destructive = true,
        onConfirm = {
            if (isTwoStep && confirmCount == 0) confirmCount = 1
            else onConfirm()
        },
        onDismissRequest = onCancel
    )
}
