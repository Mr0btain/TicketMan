package org.mr0btain.ticketman.ui.screens.shared

import androidx.compose.runtime.*
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField

/**
 * Adds or edits a note, with photos. Photos upload as soon as they're picked, and are only kept by the server
 * once the note is saved
 * @param label Dialog title, like "Add note"
 */
@Composable
fun EditNoteDialog(
    seed: NoteSeed,
    label: String = "",
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!isVisible) return

    var isUploading by remember { mutableStateOf(false) }

    TmDialog(
        title = label.ifBlank { "Note" },
        onDismissRequest = onDismissRequest,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = if (isUploading) "Uploading photos" else "Save note",
                // Saving mid upload would leave the photo off the note
                confirmEnabled = seed.message.isNotBlank() && !isUploading,
                onCancel = onDismissRequest,
                onConfirm = {
                    onConfirm()
                    onDismissRequest()
                }
            )
        }
    ) {
        TmTextField(value = seed.author, onValueChange = { seed.changeAuthor(it) }, label = "Author")

        TmTextField(
            value = seed.message,
            onValueChange = { seed.changeMessage(it) },
            label = "Note",
            singleLine = false,
            minLines = 3
        )

        PhotoEditor(
            images = seed.images,
            onAdd = { seed.addImage(it) },
            onRemove = { seed.deleteImage(it.identifier) },
            onUploadingChange = { isUploading = it }
        )
    }
}
