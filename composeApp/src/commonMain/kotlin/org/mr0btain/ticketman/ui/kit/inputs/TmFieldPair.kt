package org.mr0btain.ticketman.ui.kit.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** Two fields side by side in a form, each gets a modifier that splits the row evenly */
@Composable
fun TmFieldPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
        first(Modifier.weight(1f))
        second(Modifier.weight(1f))
    }
}
