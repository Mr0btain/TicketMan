package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.utils.ImageUtils
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.ui.kit.data.ImagePager
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmLoading
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.image

/**
 * Adds or edits a note, with photos
 * @param label Dialog title, like "Add note"
 */
@Composable
fun EditNoteDialog(
    seed: NoteSeed,
    label: String = "",
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    if (!isVisible) return

    var isLoading by remember { mutableStateOf(false) }

    // TODO: Add a dialog for editing image details
    val launcher = rememberFilePickerLauncher(type = FileKitType.Image) { file ->
        file?.let {
            scope.launch {
                isLoading = true

                val newImage = StoredImage()

                // Save image to database, resized to a max of 1080p
                val saved = ImageUtils.saveImageFromFile(
                    parentId = seed.identifier,
                    identifier = newImage.identifier,
                    file = it
                )

                if (saved) seed.addImage(newImage)

                isLoading = false
            }
        }
    }

    TmDialog(
        title = label.ifBlank { "Note" },
        onDismissRequest = onDismissRequest,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = "Save note",
                confirmEnabled = seed.message.isNotBlank(),
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

        TmButton(
            text = "Add photo",
            onClick = { launcher.launch() },
            variant = TmButtonVariant.SECONDARY,
            size = TmControlSize.SMALL,
            leadingIcon = Res.drawable.image
        )

        if (seed.images.isNotEmpty() || isLoading) {
            Box(
                modifier = Modifier
                    .height(160.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ImagePager(
                    modifier = Modifier
                        .alpha(if (isLoading) 0.5f else 1f)
                        .blur(if (isLoading) 8.dp else 0.dp),
                    storedImages = seed.images,
                    onImageClick = { },
                    coroutineScope = scope,
                    clickEnabled = false
                )

                if (isLoading) TmLoading(text = "Adding photo")
            }
        }
    }
}
