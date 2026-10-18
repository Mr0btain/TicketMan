package org.mr0btain.ticketman.ui.kit.images

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.image.LocalImageClient
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.feedback.TmProgressBar
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.image

/**
 * Photos being edited in a dialog. Picked photos upload straight away, with progress and any failure shown, and
 * can be removed from the pager. The server only keeps them once the record they're on is saved
 * @param onAdd Gets each photo once it's uploaded
 * @param onUploadingChange True while photos are going up, so the dialog can hold off saving
 */
@Composable
fun TmPhotoEditor(
    images: List<StoredImage>,
    onAdd: (StoredImage) -> Unit,
    onRemove: (StoredImage) -> Unit,
    modifier: Modifier = Modifier,
    onUploadingChange: (Boolean) -> Unit = {},
    buttonText: String = "Add photos"
) {
    val imageClient = LocalImageClient.current
    val scope = rememberCoroutineScope()

    // Photos still to send, and how far along the current one is
    var waiting by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var uploadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(waiting > 0) { onUploadingChange(waiting > 0) }

    val launcher = rememberFilePickerLauncher(type = FileKitType.Image, mode = FileKitMode.Multiple()) { files ->
        if (files.isNullOrEmpty() || imageClient == null) return@rememberFilePickerLauncher

        uploadError = null
        waiting += files.size

        scope.launch {
            files.forEach { file ->
                progress = 0f

                when (val result = imageClient.upload(file) { progress = it }) {
                    is Result.Success -> onAdd(result.data)
                    is Result.Error -> uploadError = "${file.name}: ${result.error.message}"
                }

                waiting--
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
        TmButton(
            text = buttonText,
            onClick = { launcher.launch() },
            enabled = imageClient != null,
            variant = TmButtonVariant.SECONDARY,
            size = TmControlSize.SMALL,
            leadingIcon = Res.drawable.image
        )

        if (waiting > 0) {
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

        if (images.isNotEmpty()) {
            TmImagePager(
                modifier = Modifier.height(160.dp).fillMaxWidth(),
                storedImages = images,
                coroutineScope = scope,
                clickEnabled = false,
                onRemove = onRemove
            )
        }
    }
}
