package org.mr0btain.ticketman.ui.composables.image

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.data.image.StoredImage

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImagePopup(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    isDarkMode: Boolean,
    images: List<ImageData>
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformInsets = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .height(600.dp)
                .width(600.dp),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 4.dp,
            color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            ImagePager(
                modifier = Modifier.fillMaxSize(),
                imageData = images,
                onImageClick = {},
                imageMaxWidth = 400.dp,
                imageMaxHeight = 400.dp,
                clickEnabled = false
            )
        }
    }
}