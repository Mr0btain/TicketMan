package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.foundation.GlassSurface
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Card that groups things on a screen, with an optional title row
 * @param actions Buttons on the right of the title row
 */
@Composable
fun TmPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: DrawableResource? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = TmTheme.spacing

    TmSurface(modifier = modifier) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            if (title != null) {
                TmPaneHeader(title = title, subtitle = subtitle, icon = icon, actions = actions, padded = false)
            }

            content()
        }
    }
}

/**
 * Title row for a pane or panel: icon, title and actions on the right
 * @param padded Adds the usual padding, turn off when the parent already pads
 */
@Composable
fun TmPaneHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource? = null,
    padded: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (padded) Modifier.padding(horizontal = spacing.lg, vertical = spacing.md) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.textMuted
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TmTheme.typography.subtitle,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TmTheme.typography.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/** [TmPaneHeader] on glass with a line under it, for pinning over a list that scrolls beneath it */
@Composable
fun TmStickyHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = TmTheme.shapes.panel) {
        Column {
            TmPaneHeader(title = title, subtitle = subtitle, icon = icon, actions = actions)
            content()
            TmDivider()
        }
    }
}
