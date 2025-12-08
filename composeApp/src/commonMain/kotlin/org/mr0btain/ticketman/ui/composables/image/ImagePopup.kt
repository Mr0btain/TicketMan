package org.mr0btain.ticketman.ui.composables.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.ui.dialog.FullScreenDialog

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImagePopup(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    isVisible: Boolean,
    images: List<StoredImage>
) {
    if (isVisible && images.isNotEmpty()) {
        FullScreenDialog(
            modifier = modifier,
            onDismiss = onDismiss
        ) {
            ImagePager(
                storedImages = images,
                clickEnabled = false
            )
        }
    }
}