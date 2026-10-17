package org.mr0btain.ticketman.ui.kit.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * How floating layers are drawn
 * @param reduceTransparency Draws glass as a solid surface, for readability or slower machines
 */
@Immutable
data class TmGlass(val reduceTransparency: Boolean = false)

val LocalTmColors = staticCompositionLocalOf { TmPalettes.default.dark }
val LocalTmTypography = staticCompositionLocalOf<TmTypography> { error("TmTypography is provided by TmTheme") }
val LocalTmShapes = staticCompositionLocalOf { TmShapes() }
val LocalTmSpacing = staticCompositionLocalOf { TmSpacing() }
val LocalTmGlass = staticCompositionLocalOf { TmGlass() }

/** Set by TmBackdrop, glass surfaces blur whatever is registered with it. Null draws glass without blur */
val LocalTmHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Theme for the whole app. Also sets up MaterialTheme from the same values so
 * Material components that haven't been replaced by the kit still match
 */
@Composable
fun TmTheme(
    palette: TmPalette = TmPalettes.default,
    font: TmFont = TmFonts.default,
    darkTheme: Boolean = true,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = palette.colors(darkTheme)
    val textFamily = font.family()
    val monoFamily = TmFonts.mono()
    val typography = remember(textFamily, monoFamily) { TmTypography.build(textFamily, monoFamily) }
    val shapes = remember { TmShapes() }
    val colorScheme = remember(colors) { colors.toColorScheme() }
    val selectionColors = remember(colors) {
        TextSelectionColors(handleColor = colors.accent.color, backgroundColor = colors.accent.color.copy(alpha = 0.32f))
    }

    CompositionLocalProvider(
        LocalTmColors provides colors,
        LocalTmTypography provides typography,
        LocalTmShapes provides shapes,
        LocalTmSpacing provides TmSpacing(),
        LocalTmGlass provides TmGlass(reduceTransparency)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography.toMaterial(),
            shapes = shapes.toMaterial()
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors, content = content)
        }
    }
}

/** Current theme values, use these in place of hard coded colors, sizes and shapes */
object TmTheme {
    val colors: TmColors
        @Composable @ReadOnlyComposable get() = LocalTmColors.current

    val typography: TmTypography
        @Composable @ReadOnlyComposable get() = LocalTmTypography.current

    val shapes: TmShapes
        @Composable @ReadOnlyComposable get() = LocalTmShapes.current

    val spacing: TmSpacing
        @Composable @ReadOnlyComposable get() = LocalTmSpacing.current

    val glass: TmGlass
        @Composable @ReadOnlyComposable get() = LocalTmGlass.current
}
