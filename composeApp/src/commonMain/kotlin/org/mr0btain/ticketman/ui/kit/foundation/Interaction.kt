package org.mr0btain.ticketman.ui.kit.foundation

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

// Hover, press and keyboard focus look the same on everything clickable in the kit

/** Alpha of the [overlay] tint while hovered and while pressed */
private const val HOVER_ALPHA = 0.06f
private const val PRESSED_ALPHA = 0.12f

/**
 * Makes something clickable with the kit's hover and press tint and a focus ring for keyboard users
 * @param overlay Tint drawn over the content on hover and press, usually the content color
 */
@Composable
fun Modifier.tmClickable(
    onClick: () -> Unit,
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    overlay: Color = TmTheme.colors.text,
    interactionSource: MutableInteractionSource? = null
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    // Clicking also focuses on desktop, the ring is only for focus that came from the keyboard
    var focusFromPointer by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> focusFromPointer = true
                is FocusInteraction.Unfocus -> focusFromPointer = false
            }
        }
    }
    val tint by animateFloatAsState(
        when {
            !enabled -> 0f
            pressed -> PRESSED_ALPHA
            hovered -> HOVER_ALPHA
            else -> 0f
        }
    )

    return this
        .tmFocusRing(focused && enabled && !focusFromPointer, shape)
        .clip(shape)
        .hoverable(source, enabled)
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
        .drawWithContent {
            drawContent()
            if (tint > 0f) drawRect(overlay.copy(alpha = tint))
        }
}

/** Accent ring shown while something has keyboard focus */
@Composable
fun Modifier.tmFocusRing(focused: Boolean, shape: Shape): Modifier =
    if (focused) border(2.dp, TmTheme.colors.accent.color, shape) else this
