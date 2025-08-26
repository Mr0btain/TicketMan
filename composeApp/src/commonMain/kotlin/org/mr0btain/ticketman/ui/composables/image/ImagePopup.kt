package org.mr0btain.ticketman.ui.composables.image

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.mr0btain.ticketman.components.utils.decodeBase64ToImageBitmap

@Composable
fun ImagePopup(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    isDarkMode: Boolean,
    images: List<String>
) {
    Dialog(
        onDismissRequest = onDismiss
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
                modifier = Modifier.fillMaxWidth(),
                imageData = images,
                onImageClick = {},
                imageMaxWidth = 600.dp,
                imageMaxHeight = 600.dp
            )
        }
    }
}