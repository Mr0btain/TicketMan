package org.mr0btain.ticketman.ui.kit.selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.buttons.TM_DISABLED_ALPHA
import org.mr0btain.ticketman.ui.kit.foundation.glass
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.check

/**
 * Glass popup menu, opens under whatever it's placed next to. Fill it with [TmMenuItem]s
 * @param focusable False keeps keyboard focus where it was, for menus that open while typing
 */
@Composable
fun TmMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    focusable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = TmTheme.shapes.panel

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .widthIn(min = 160.dp)
            .glass(shape, border = false)
            .padding(horizontal = TmTheme.spacing.xs),
        offset = offset,
        scrollState = rememberScrollState(),
        properties = PopupProperties(focusable = focusable),
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, TmTheme.colors.hairline),
        content = content
    )
}

/**
 * One choice in a [TmMenu]
 * @param selected Shows a check and the accent color, for the current choice in a dropdown
 */
@Composable
fun TmMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    TmMenuItem(
        text = AnnotatedString(text),
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        leading = leading,
        trailingText = trailingText,
        selected = selected,
        enabled = enabled,
        destructive = destructive
    )
}

/** Same as the String version, for text with styled parts like search matches */
@Composable
fun TmMenuItem(
    text: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val colors = TmTheme.colors
    val contentColor = when {
        destructive -> colors.danger.color
        selected -> colors.accent.color
        else -> colors.text
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .alpha(if (enabled) 1f else TM_DISABLED_ALPHA)
            .tmClickable(onClick = onClick, shape = TmTheme.shapes.chip, enabled = enabled)
            .padding(horizontal = TmTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> leading()
            leadingIcon != null -> Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = contentColor
            )
        }

        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = text,
            style = TmTheme.typography.body,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.weight(1f, fill = true).widthIn(min = TmTheme.spacing.md))

        if (trailingText != null) {
            Text(text = trailingText, style = TmTheme.typography.caption, color = colors.textMuted)
        }

        if (selected) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(Res.drawable.check),
                contentDescription = "Selected",
                tint = colors.accent.color
            )
        }
    }
}
