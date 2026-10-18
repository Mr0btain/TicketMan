package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.foundation.GlassSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.x

/**
 * Dialog that fills most of the window. Closes from the X in its corner, Escape, or clicking around it. Used for
 * looking at photos
 * @param onKeyEvent Gets key presses first (Escape is handled after), return true when it used one
 */
@Composable
fun TmFullScreenDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable (() -> Unit)
) {
    val focus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .focusRequester(focus)
                .onKeyEvent { event ->
                    when {
                        onKeyEvent(event) -> true
                        event.type == KeyEventType.KeyDown && event.key == Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                }
                .focusable()
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

                TmIconButton(
                    modifier = Modifier.align(Alignment.TopEnd).padding(TmTheme.spacing.sm),
                    icon = Res.drawable.x,
                    contentDescription = "Close (Esc)",
                    variant = TmButtonVariant.SECONDARY,
                    onClick = onDismiss
                )
            }
        }

        // Key presses only arrive once something in the dialog has focus
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
