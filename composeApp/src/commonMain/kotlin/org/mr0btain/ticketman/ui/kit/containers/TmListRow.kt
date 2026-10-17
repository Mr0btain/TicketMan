package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.buttons.TM_DISABLED_ALPHA
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.selection.TmSwitch
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * A row in a list or settings group: leading icon or slot, title and subtitle, and a trailing slot
 * @param selected Marks the row with an accent bar, for the open item in a list
 * @param onClick Makes the row clickable, leave null for rows that only hold a control
 */
@Composable
fun TmListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    val shape = TmTheme.shapes.control

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else TM_DISABLED_ALPHA)
            .background(if (selected) colors.accent.soft else Color.Transparent, shape)
            .then(if (onClick != null) Modifier.tmClickable(onClick = onClick, shape = shape, enabled = enabled) else Modifier)
            .padding(end = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent bar for the selected row, kept as space otherwise so rows don't shift
        Box(
            Modifier
                .width(3.dp)
                .heightIn(min = 24.dp)
                .padding(vertical = spacing.xs)
                .background(if (selected) colors.accent.color else Color.Transparent, TmTheme.shapes.pill)
        )

        when {
            leading != null -> leading()
            icon != null -> Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (selected) colors.accent.color else colors.textMuted
            )
        }

        Column(Modifier.weight(1f).padding(vertical = spacing.sm)) {
            Text(
                text = title,
                style = TmTheme.typography.label,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TmTheme.typography.caption,
                    color = colors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        trailing?.invoke()
    }
}

/** [TmListRow] with a switch on the right, the whole row toggles it */
@Composable
fun TmSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource? = null,
    enabled: Boolean = true
) {
    TmListRow(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = { TmSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) }
    )
}
