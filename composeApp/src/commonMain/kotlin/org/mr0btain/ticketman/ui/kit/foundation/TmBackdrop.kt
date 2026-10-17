package org.mr0btain.ticketman.ui.kit.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.mr0btain.ticketman.ui.kit.theme.LocalTmHazeState
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** Layer order for glass sources, glass blurs every source below the one it sits in */
object TmLayers {
    const val BACKDROP = 0f
    const val CONTENT = 1f
}

/**
 * App background: the base color with one soft glow in the top left, which glass layers pick up when they blur.
 * Glass surfaces inside [content] blur the backdrop and anything marked with [glassSource]
 */
@Composable
fun TmBackdrop(
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    content: @Composable BoxScope.() -> Unit
) {
    val colors = TmTheme.colors

    Box(modifier.background(colors.background)) {
        Box(
            Modifier
                .matchParentSize()
                .hazeSource(hazeState, zIndex = TmLayers.BACKDROP)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(colors.glow, Color.Transparent),
                            center = Offset(size.width * 0.15f, size.height * 0.05f),
                            radius = maxOf(size.width, size.height) * 0.7f
                        )
                    )
                }
        )

        CompositionLocalProvider(LocalTmHazeState provides hazeState) {
            content()
        }
    }
}

/**
 * Lets glass layers drawn on top of this blur it, like a list scrolling under a sticky header.
 * Does nothing outside a [TmBackdrop]
 */
@Composable
fun Modifier.glassSource(zIndex: Float = TmLayers.CONTENT): Modifier {
    val state = LocalTmHazeState.current ?: return this
    return hazeSource(state, zIndex = zIndex)
}
