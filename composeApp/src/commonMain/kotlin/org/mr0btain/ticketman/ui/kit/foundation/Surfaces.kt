package org.mr0btain.ticketman.ui.kit.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import org.mr0btain.ticketman.ui.kit.theme.LocalTmHazeState
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** How far a solid surface sits from the background */
enum class TmSurfaceLevel {
    /** Cards and panels on the background */
    BASE,

    /** Things on a card, like menus or a row being hovered */
    RAISED,

    /** Wells inside a card, like input fills */
    SUNKEN
}

/**
 * Opaque surface for cards, panels and list rows. Solid so text stays sharp
 * @param onClick Makes the whole surface clickable with the kit's hover and focus states
 */
@Composable
fun TmSurface(
    modifier: Modifier = Modifier,
    level: TmSurfaceLevel = TmSurfaceLevel.BASE,
    shape: Shape = TmTheme.shapes.panel,
    border: Boolean = true,
    color: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = TmTheme.colors
    val fill = if (color.isSpecified) color else when (level) {
        TmSurfaceLevel.BASE -> colors.surface
        TmSurfaceLevel.RAISED -> colors.surfaceRaised
        TmSurfaceLevel.SUNKEN -> colors.surfaceSunken
    }

    Box(
        modifier
            .clip(shape)
            .background(fill)
            .then(if (border) Modifier.border(1.dp, colors.hairline, shape) else Modifier)
            .then(if (onClick != null) Modifier.tmClickable(onClick = onClick, shape = shape) else Modifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.text) {
            content()
        }
    }
}

/**
 * Smoked glass for things that float over content: nav rail, dialogs, menus, sticky headers.
 * Blurs what's behind it inside a [TmBackdrop], otherwise (or with reduce transparency on) it's a plain fill
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = TmTheme.shapes.panel,
    border: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.glass(shape, border)) {
        CompositionLocalProvider(LocalContentColor provides TmTheme.colors.text) {
            content()
        }
    }
}

/** The fill [GlassSurface] uses, for wrapping things that bring their own container like menus */
@Composable
fun Modifier.glass(
    shape: Shape = TmTheme.shapes.panel,
    border: Boolean = true
): Modifier {
    val colors = TmTheme.colors
    val hazeState = LocalTmHazeState.current
    val solid = TmTheme.glass.reduceTransparency

    val blurStyle = remember(colors) {
        HazeBlurStyle {
            blurRadius(28.dp)
            noiseFactor(0.04f)
            backgroundColor(colors.background)
            colorEffects(listOf(HazeColorEffect.tint(colors.glass)))
        }
    }

    val fill = when {
        solid -> Modifier.background(colors.surfaceRaised)
        hazeState != null -> Modifier.hazeBlur(HazeInput.Sources(hazeState), style = blurStyle)
        else -> Modifier.background(colors.glass)
    }

    return this
        .clip(shape)
        .then(fill)
        .then(if (border) Modifier.border(1.dp, colors.hairline, shape) else Modifier)
}

/** One pixel line between things, sideways unless [vertical] */
@Composable
fun TmDivider(
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    Box(
        modifier
            .then(if (vertical) Modifier.fillMaxHeight().width(1.dp) else Modifier.fillMaxWidth().height(1.dp))
            .background(TmTheme.colors.hairline)
    )
}
