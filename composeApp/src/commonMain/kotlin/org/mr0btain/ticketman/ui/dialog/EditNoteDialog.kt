package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.note.NoteSeed
import org.mr0btain.ticketman.components.image.LocalImageClient
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.ui.kit.data.ImagePager
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.TmProgressBar
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.image

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
    onDismissRequest: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
) {
    if (!isVisible) return

    val imageClient = LocalImageClient.current

    // Photos still to send, and how far along the current one is
    var waiting by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    val isUploading = waiting > 0

    val launcher = rememberFilePickerLauncher(type = FileKitType.Image, mode = FileKitMode.Multiple()) { files ->
        if (files.isNullOrEmpty() || imageClient == null) return@rememberFilePickerLauncher

        uploadError = null
        waiting += files.size

        scope.launch {
            files.forEach { file ->
                progress = 0f

                when (val result = imageClient.upload(file) { progress = it }) {
                    is Result.Success -> seed.addImage(result.data)
                    is Result.Error -> uploadError = "${file.name}: ${result.error.message}"
                }

                waiting--
            }
        }
    }

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

        TmButton(
            text = "Add photos",
            onClick = { launcher.launch() },
            enabled = imageClient != null,
            variant = TmButtonVariant.SECONDARY,
            size = TmControlSize.SMALL,
            leadingIcon = Res.drawable.image
        )

        if (isUploading) {
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
                Text(
                    text = if (waiting == 1) "Uploading photo" else "Uploading $waiting photos",
                    style = TmTheme.typography.caption,
                    color = TmTheme.colors.textMuted
                )
                TmProgressBar(progress = progress)
            }
        }

        uploadError?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR, title = "Photo not added") }

        if (seed.images.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .height(160.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ImagePager(
                    storedImages = seed.images,
                    coroutineScope = scope,
                    clickEnabled = false,
                    onRemove = { image -> seed.deleteImage(image.identifier) }
                )
            }
        }
    }
}
