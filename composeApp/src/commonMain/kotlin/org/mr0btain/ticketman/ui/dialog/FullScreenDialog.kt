package org.mr0btain.ticketman.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mr0btain.ticketman.ui.kit.foundation.GlassSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** Dialog that fills most of the window, clicking around it closes it. Used for looking at photos */
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
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() }
                .padding(80.dp)
                .fillMaxSize()
        ) {
            GlassSurface(
                modifier = Modifier.clickable(false) {},
                shape = TmTheme.shapes.sheet
            ) {
                content()
            }
        }
    }
}
