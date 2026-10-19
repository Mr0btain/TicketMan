package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.foundation.TmGlassSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Glass dialog with a title, optional description, content and action row.
 * Use TmButtonRow for [actions] so dialogs end the same way
 * @param dismissOnOutsideClick Turn off for forms that would lose typed text
 */
@Composable
fun TmDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    maxWidth: Dp = 480.dp,
    dismissOnOutsideClick: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = dismissOnOutsideClick)
    ) {
        TmDialogSurface(
            // Desktop's dialog closes on Escape by itself, the browser's doesn't. Handled here it works on both,
            // and consuming the key stops desktop closing it twice
            modifier = modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismissRequest()
                    true
                } else false
            },
            title = title,
            description = description,
            maxWidth = maxWidth,
            actions = actions,
            content = content
        )
    }
}

/**
 * What [TmDialog] draws inside its window, split out so it can be shown in place in the gallery
 * @param scrollable Scrolls content taller than the window. Turn off when placed inside something that already scrolls
 */
@Composable
fun TmDialogSurface(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    maxWidth: Dp = 480.dp,
    scrollable: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    val shape = TmTheme.shapes.sheet

    TmGlassSurface(
        modifier = modifier
            .widthIn(min = 280.dp, max = maxWidth)
            .shadow(24.dp, shape, clip = false),
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(text = title, style = TmTheme.typography.title, color = colors.text)

                if (description != null) {
                    Text(text = description, style = TmTheme.typography.body, color = colors.textMuted)
                }
            }

            content()

            actions?.invoke()
        }
    }
}

/**
 * Asks before doing something, like deleting. Say what will happen in [title] and name the action in [confirmText]
 * @param destructive Uses the danger color for the confirm button
 */
@Composable
fun TmConfirmDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    message: String? = null,
    destructive: Boolean = false,
    loading: Boolean = false
) {
    TmDialog(
        title = title,
        description = message,
        onDismissRequest = onDismissRequest,
        actions = {
            TmButtonRow(
                confirmText = confirmText,
                confirmVariant = if (destructive) TmButtonVariant.DANGER else TmButtonVariant.PRIMARY,
                loading = loading,
                onConfirm = onConfirm,
                onCancel = onDismissRequest
            )
        }
    )
}
