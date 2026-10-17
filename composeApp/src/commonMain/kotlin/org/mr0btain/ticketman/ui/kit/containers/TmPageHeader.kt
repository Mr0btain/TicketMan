package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.arrow_left

/**
 * Top of a page: an optional back button, a big title and buttons on the right
 * @param titleContent Drawn in place of [title], for titles with badges or a ticket number
 */
@Composable
fun TmPageHeader(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val spacing = TmTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            TmIconButton(icon = Res.drawable.arrow_left, contentDescription = "Back", onClick = onBack)
        }

        Column(Modifier.weight(1f)) {
            if (titleContent != null) titleContent()
            else Text(text = title, style = TmTheme.typography.display, color = TmTheme.colors.text)

            if (subtitle != null) {
                Text(text = subtitle, style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/** Heading over a group of fields in a form or dialog */
@Composable
fun TmSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(top = TmTheme.spacing.sm),
        text = text,
        style = TmTheme.typography.subtitle,
        color = TmTheme.colors.text
    )
}
