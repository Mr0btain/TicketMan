package org.mr0btain.ticketman.ui.kit.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Cancel and confirm buttons, right aligned with the confirm button last.
 * Name the confirm button for what it does, like "Delete ticket" or "Save changes"
 * @param confirmVariant Use DANGER when confirming deletes something
 */
@Composable
fun TmButtonRow(
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = "Cancel",
    confirmVariant: TmButtonVariant = TmButtonVariant.PRIMARY,
    confirmEnabled: Boolean = true,
    loading: Boolean = false,
    size: TmControlSize = TmControlSize.MEDIUM
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TmButton(
            text = cancelText,
            onClick = onCancel,
            variant = TmButtonVariant.GHOST,
            size = size
        )

        TmButton(
            text = confirmText,
            onClick = onConfirm,
            variant = confirmVariant,
            size = size,
            enabled = confirmEnabled,
            loading = loading
        )
    }
}
