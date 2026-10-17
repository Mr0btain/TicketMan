package org.mr0btain.ticketman.ui.kit.selection

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.buttons.TM_DISABLED_ALPHA
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** Chip that turns on and off, for filters like "Open tickets only" */
@Composable
fun TmFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    enabled: Boolean = true
) {
    val colors = TmTheme.colors
    val shape = TmTheme.shapes.chip
    val fill by animateColorAsState(if (selected) colors.accent.soft else Color.Transparent)
    val content = if (selected) colors.accent.color else colors.textMuted

    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else TM_DISABLED_ALPHA)
            .height(TmControlSize.SMALL.height)
            .background(fill, shape)
            .border(1.dp, if (selected) colors.accent.color.copy(alpha = 0.4f) else colors.outline, shape)
            .tmClickable(onClick = onClick, shape = shape, enabled = enabled, role = Role.Checkbox, overlay = content)
            .padding(horizontal = TmTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                modifier = Modifier.size(14.dp),
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = content
            )
        }

        Text(text = text, style = TmTheme.typography.label, color = content, maxLines = 1)
    }
}

/**
 * A few options side by side where one is always picked, like Dark / Light
 * @param optionLabel Text shown for an option
 */
@Composable
fun <T> TmSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    size: TmControlSize = TmControlSize.MEDIUM
) {
    val colors = TmTheme.colors
    val trackShape = TmTheme.shapes.control
    val segmentShape = TmTheme.shapes.chip

    Column(
        modifier = modifier.alpha(if (enabled) 1f else TM_DISABLED_ALPHA),
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)
    ) {
        if (label != null) {
            Text(text = label, style = TmTheme.typography.caption, color = colors.textMuted)
        }

        Row(
            modifier = Modifier
                .height(size.height)
                .background(colors.surfaceSunken, trackShape)
                .border(1.dp, colors.hairline, trackShape)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                // In light themes the plain surface is the brightest layer, so it stands out best on the sunken track
                val pickedFill = if (colors.isDark) colors.surfaceRaised else colors.surface
                val fill by animateColorAsState(if (isSelected) pickedFill else Color.Transparent)

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(if (isSelected && !colors.isDark) Modifier.shadow(1.dp, segmentShape) else Modifier)
                        .background(fill, segmentShape)
                        .border(1.dp, if (isSelected) colors.outline else Color.Transparent, segmentShape)
                        .tmClickable(
                            onClick = { onSelect(option) },
                            shape = segmentShape,
                            enabled = enabled,
                            role = Role.RadioButton
                        )
                        .padding(horizontal = TmTheme.spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = optionLabel(option),
                        style = TmTheme.typography.label,
                        color = if (isSelected) colors.text else colors.textMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
