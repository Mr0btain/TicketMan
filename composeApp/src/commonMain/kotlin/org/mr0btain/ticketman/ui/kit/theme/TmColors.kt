package org.mr0btain.ticketman.ui.kit.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * A color with what goes on top of it, plus a faint version for backgrounds like badges and selected rows
 * @param color Used for solid fills, and for text or icons sitting on [soft]
 * @param onColor Text or icons on a solid [color] fill
 * @param soft Faint fill made from [color]
 */
@Immutable
data class TmTone(
    val color: Color,
    val onColor: Color,
    val soft: Color
) {
    companion object {
        /** Builds a tone whose soft fill is [color] at [softAlpha] */
        fun of(color: Color, onColor: Color, softAlpha: Float): TmTone =
            TmTone(color = color, onColor = onColor, soft = color.copy(alpha = softAlpha))
    }
}

/**
 * Every color the kit uses. Each palette fills one of these for dark and one for light
 *
 * Layers from back to front: [background] (with [glow]) -> [surface] cards -> [surfaceRaised] for things on cards.
 * [glass] is for floating layers that blur what's behind them, [surfaceSunken] is for input fills.
 */
@Immutable
data class TmColors(
    val isDark: Boolean,
    val background: Color,
    val glow: Color,
    val glass: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val hairline: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: TmTone,
    val success: TmTone,
    val warning: TmTone,
    val danger: TmTone,
    val neutral: TmTone,
    val scrim: Color = Color.Black.copy(alpha = if (isDark) 0.6f else 0.32f)
) {
    /**
     * Material colors made from ours, so Material components we haven't replaced still match.
     * Containers are made opaque since Material draws them as solid fills
     */
    fun toColorScheme(): ColorScheme {
        val base = if (isDark) darkColorScheme() else lightColorScheme()

        return base.copy(
            primary = accent.color,
            onPrimary = accent.onColor,
            primaryContainer = accent.soft.compositeOver(surface),
            onPrimaryContainer = text,
            inversePrimary = accent.color,
            secondary = neutral.color,
            onSecondary = neutral.onColor,
            secondaryContainer = accent.soft.compositeOver(surfaceRaised),
            onSecondaryContainer = text,
            tertiary = warning.color,
            onTertiary = warning.onColor,
            tertiaryContainer = warning.soft.compositeOver(surface),
            onTertiaryContainer = text,
            error = danger.color,
            onError = danger.onColor,
            errorContainer = danger.soft.compositeOver(surface),
            onErrorContainer = text,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceRaised,
            onSurfaceVariant = textMuted,
            surfaceTint = accent.color,
            inverseSurface = text,
            inverseOnSurface = background,
            outline = outline,
            outlineVariant = hairline.compositeOver(surface),
            scrim = scrim,
            surfaceBright = surfaceRaised,
            surfaceDim = background,
            surfaceContainerLowest = surfaceSunken,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceRaised,
            surfaceContainerHighest = surfaceRaised
        )
    }
}
