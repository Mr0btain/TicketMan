package org.mr0btain.ticketman.ui.kit.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmColors
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** Alpha for anything in the kit that's turned off */
const val TM_DISABLED_ALPHA = 0.4f

enum class TmButtonVariant {
    /** The one main action in a view */
    PRIMARY,

    /** Other actions next to the main one */
    SECONDARY,

    /** Low key actions, like cancel or toolbar buttons */
    GHOST,

    /** Actions that delete or can't be undone */
    DANGER
}

/** Fill, content and border colors for a variant */
internal data class TmButtonColors(val container: Color, val content: Color, val border: Color)

internal fun TmColors.buttonColors(variant: TmButtonVariant): TmButtonColors = when (variant) {
    TmButtonVariant.PRIMARY -> TmButtonColors(accent.color, accent.onColor, Color.Transparent)
    TmButtonVariant.SECONDARY -> TmButtonColors(surfaceRaised, text, outline)
    TmButtonVariant.GHOST -> TmButtonColors(Color.Transparent, text, Color.Transparent)
    TmButtonVariant.DANGER -> TmButtonColors(danger.color, danger.onColor, Color.Transparent)
}

/**
 * The button used everywhere. Name it for what happens: "Save customer", not "Submit"
 * @param loading Shows a spinner in place of the icon and ignores clicks
 */
@Composable
fun TmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TmButtonVariant = TmButtonVariant.PRIMARY,
    size: TmControlSize = TmControlSize.MEDIUM,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: DrawableResource? = null,
    trailingIcon: DrawableResource? = null,
    interactionSource: MutableInteractionSource? = null
) {
    val colors = TmTheme.colors.buttonColors(variant)
    val shape = TmTheme.shapes.control
    val clickable = enabled && !loading
    val textStyle: TextStyle = if (size == TmControlSize.LARGE) TmTheme.typography.subtitle else TmTheme.typography.label

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = size.height)
            .alpha(if (enabled) 1f else TM_DISABLED_ALPHA)
            .background(colors.container, shape)
            .border(1.dp, colors.border, shape)
            .tmClickable(
                onClick = onClick,
                shape = shape,
                enabled = clickable,
                overlay = colors.content,
                interactionSource = interactionSource
            )
            .padding(PaddingValues(horizontal = size.horizontalPadding)),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.size(size.iconSize),
                        color = colors.content,
                        strokeWidth = 2.dp
                    )

                    leadingIcon != null -> Icon(
                        modifier = Modifier.size(size.iconSize),
                        painter = painterResource(leadingIcon),
                        contentDescription = null
                    )
                }

                Text(
                    text = text,
                    style = textStyle,
                    color = colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (trailingIcon != null) {
                    Icon(
                        modifier = Modifier.size(size.iconSize),
                        painter = painterResource(trailingIcon),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

/**
 * Square button with just an icon, ghost by default for toolbars and rows
 * @param contentDescription Read out by screen readers, say what the button does
 */
@Composable
fun TmIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TmButtonVariant = TmButtonVariant.GHOST,
    size: TmControlSize = TmControlSize.MEDIUM,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified
) {
    val colors = TmTheme.colors.buttonColors(variant)
    val shape = TmTheme.shapes.control

    Box(
        modifier = modifier
            .size(size.height)
            .alpha(if (enabled) 1f else TM_DISABLED_ALPHA)
            .background(colors.container, shape)
            .border(1.dp, colors.border, shape)
            .tmClickable(onClick = onClick, shape = shape, enabled = enabled, overlay = colors.content),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(size.iconSize),
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (tint.isSpecified) tint else colors.content
        )
    }
}
