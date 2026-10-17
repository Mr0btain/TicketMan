package org.mr0btain.ticketman.ui.kit.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.buttons.TM_DISABLED_ALPHA
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * The look shared by every field: label above, a sunken well, and help or error text below.
 * Text fields, dropdowns and search bars all draw through this so they match
 * @param error Shown in place of [supportingText] and turns the border red
 * @param focused Draws the accent border
 */
@Composable
fun TmFieldFrame(
    modifier: Modifier = Modifier,
    label: String? = null,
    supportingText: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    focused: Boolean = false,
    size: TmControlSize = TmControlSize.MEDIUM,
    leadingIcon: DrawableResource? = null,
    trailing: (@Composable () -> Unit)? = null,
    wellModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = TmTheme.colors
    val shape = TmTheme.shapes.control
    val spacing = TmTheme.spacing

    val borderColor = when {
        error != null -> colors.danger.color
        focused -> colors.accent.color
        else -> colors.outline
    }

    Column(
        modifier = modifier.alpha(if (enabled) 1f else TM_DISABLED_ALPHA),
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        if (label != null) {
            Text(
                text = label,
                style = TmTheme.typography.caption,
                color = if (error != null) colors.danger.color else colors.textMuted
            )
        }

        Row(
            modifier = wellModifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = size.height)
                .background(colors.surfaceSunken, shape)
                .border(if (focused || error != null) 1.5.dp else 1.dp, borderColor, shape)
                .padding(horizontal = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.textMuted) {
                if (leadingIcon != null) {
                    Icon(
                        modifier = Modifier.size(size.iconSize),
                        painter = painterResource(leadingIcon),
                        contentDescription = null
                    )
                }

                content()

                trailing?.invoke()
            }
        }

        val message = (error ?: supportingText)?.takeIf { it.isNotBlank() }
        if (message != null) {
            Text(
                text = message,
                style = TmTheme.typography.caption,
                color = if (error != null) colors.danger.color else colors.textMuted
            )
        }
    }
}
