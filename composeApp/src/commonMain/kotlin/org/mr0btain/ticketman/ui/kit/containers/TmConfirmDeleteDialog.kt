package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.runtime.*

/**
 * Asks before deleting something
 * @param itemName What's being deleted, like "Ticket"
 * @param isTwoStep Asks a second time, for things that take a lot with them like a customer's tickets
 */
@Composable
fun TmConfirmDeleteDialog(
    itemName: String,
    isTwoStep: Boolean = false,
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    if (!isVisible) return

    var confirmCount by remember { mutableIntStateOf(0) }
    val name = itemName.lowercase()

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
