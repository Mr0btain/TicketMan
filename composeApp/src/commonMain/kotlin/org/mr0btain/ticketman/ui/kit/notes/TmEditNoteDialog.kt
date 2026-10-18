package org.mr0btain.ticketman.ui.kit.notes

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDeleteDialog
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.images.TmPhotoEditor
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.trash_2

/**
 * Adds or edits a note, with photos. Photos upload as soon as they're picked, and are only kept by the server
 * once the note is saved. Saving waits until something has changed
 * @param label Dialog title, like "Add note"
 * @param onDelete Deletes the note being edited, asked about first. Null leaves the delete button off, and it's
 * never shown for a new note
 */
@Composable
fun TmEditNoteDialog(
    seed: NoteSeed,
    label: String = "",
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    if (!isVisible) return

    var isUploading by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    TmConfirmDeleteDialog(
        itemName = "Note",
        isVisible = confirmingDelete,
        onConfirm = {
            confirmingDelete = false
            onDelete?.invoke()
            onDismissRequest()
        },
        onCancel = { confirmingDelete = false }
    )

    TmDialog(
        title = label.ifBlank { "Note" },
        onDismissRequest = onDismissRequest,
        dismissOnOutsideClick = false,
        actions = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null && !seed.isNew) {
                    TmButton(
                        text = "Delete note",
                        onClick = { confirmingDelete = true },
                        variant = TmButtonVariant.GHOST,
                        leadingIcon = Res.drawable.trash_2
                    )
                }

                TmButtonRow(
                    modifier = Modifier.weight(1f),
                    confirmText = if (isUploading) "Uploading photos" else "Save note",
                    // Saving mid upload would leave the photo off the note
                    confirmEnabled = seed.message.isNotBlank() && seed.isDirty && !isUploading,
                    onCancel = onDismissRequest,
                    onConfirm = {
                        onConfirm()
                        onDismissRequest()
                    }
                )
            }
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

        TmPhotoEditor(
            images = seed.images,
            onAdd = { seed.addImage(it) },
            onRemove = { seed.deleteImage(it.identifier) },
            onUploadingChange = { isUploading = it }
        )
    }
}
