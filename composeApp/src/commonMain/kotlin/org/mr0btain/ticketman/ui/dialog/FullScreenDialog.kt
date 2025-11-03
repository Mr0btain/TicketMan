package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mr0btain.ticketman.ui.composables.image.ImagePager

@Composable
fun FullScreenDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    content: @Composable (() -> Unit)
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .clickable(
                    indication = null,
                    interactionSource = MutableInteractionSource(),
                ) { onDismiss() }
                .padding(80.dp)
                .fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.clickable(false) {},
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp,
                content = content
            )
        }
    }
}