package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.mr0btain.ticketman.ui.theme.backgroundDark
import org.mr0btain.ticketman.ui.theme.backgroundDarkHighContrast
import org.mr0btain.ticketman.ui.theme.backgroundDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.backgroundLight
import org.mr0btain.ticketman.ui.theme.backgroundLightHighContrast
import org.mr0btain.ticketman.ui.theme.backgroundLightMediumContrast
import org.mr0btain.ticketman.ui.theme.errorContainerDark
import org.mr0btain.ticketman.ui.theme.errorContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.errorContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.errorContainerLight
import org.mr0btain.ticketman.ui.theme.errorContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.errorContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.errorDark
import org.mr0btain.ticketman.ui.theme.errorDarkHighContrast
import org.mr0btain.ticketman.ui.theme.errorDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.errorLight
import org.mr0btain.ticketman.ui.theme.errorLightHighContrast
import org.mr0btain.ticketman.ui.theme.errorLightMediumContrast
import org.mr0btain.ticketman.ui.theme.inverseOnSurfaceDark
import org.mr0btain.ticketman.ui.theme.inverseOnSurfaceDarkHighContrast
import org.mr0btain.ticketman.ui.theme.inverseOnSurfaceDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.inverseOnSurfaceLight
import org.mr0btain.ticketman.ui.theme.inverseOnSurfaceLightHighContrast
import org.mr0btain.ticketman.ui.theme.inverseOnSurfaceLightMediumContrast
import org.mr0btain.ticketman.ui.theme.inversePrimaryDark
import org.mr0btain.ticketman.ui.theme.inversePrimaryDarkHighContrast
import org.mr0btain.ticketman.ui.theme.inversePrimaryDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.inversePrimaryLight
import org.mr0btain.ticketman.ui.theme.inversePrimaryLightHighContrast
import org.mr0btain.ticketman.ui.theme.inversePrimaryLightMediumContrast
import org.mr0btain.ticketman.ui.theme.inverseSurfaceDark
import org.mr0btain.ticketman.ui.theme.inverseSurfaceDarkHighContrast
import org.mr0btain.ticketman.ui.theme.inverseSurfaceDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.inverseSurfaceLight
import org.mr0btain.ticketman.ui.theme.inverseSurfaceLightHighContrast
import org.mr0btain.ticketman.ui.theme.inverseSurfaceLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onBackgroundDark
import org.mr0btain.ticketman.ui.theme.onBackgroundDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onBackgroundDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onBackgroundLight
import org.mr0btain.ticketman.ui.theme.onBackgroundLightHighContrast
import org.mr0btain.ticketman.ui.theme.onBackgroundLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onErrorContainerDark
import org.mr0btain.ticketman.ui.theme.onErrorContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onErrorContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onErrorContainerLight
import org.mr0btain.ticketman.ui.theme.onErrorContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.onErrorContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onErrorDark
import org.mr0btain.ticketman.ui.theme.onErrorDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onErrorDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onErrorLight
import org.mr0btain.ticketman.ui.theme.onErrorLightHighContrast
import org.mr0btain.ticketman.ui.theme.onErrorLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onPrimaryContainerDark
import org.mr0btain.ticketman.ui.theme.onPrimaryContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onPrimaryContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onPrimaryContainerLight
import org.mr0btain.ticketman.ui.theme.onPrimaryContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.onPrimaryContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onPrimaryDark
import org.mr0btain.ticketman.ui.theme.onPrimaryDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onPrimaryDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onPrimaryLight
import org.mr0btain.ticketman.ui.theme.onPrimaryLightHighContrast
import org.mr0btain.ticketman.ui.theme.onPrimaryLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onSecondaryContainerDark
import org.mr0btain.ticketman.ui.theme.onSecondaryContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onSecondaryContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onSecondaryContainerLight
import org.mr0btain.ticketman.ui.theme.onSecondaryContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.onSecondaryContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onSecondaryDark
import org.mr0btain.ticketman.ui.theme.onSecondaryDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onSecondaryDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onSecondaryLight
import org.mr0btain.ticketman.ui.theme.onSecondaryLightHighContrast
import org.mr0btain.ticketman.ui.theme.onSecondaryLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onSurfaceDark
import org.mr0btain.ticketman.ui.theme.onSurfaceDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onSurfaceDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onSurfaceLight
import org.mr0btain.ticketman.ui.theme.onSurfaceLightHighContrast
import org.mr0btain.ticketman.ui.theme.onSurfaceLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onSurfaceVariantDark
import org.mr0btain.ticketman.ui.theme.onSurfaceVariantDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onSurfaceVariantDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onSurfaceVariantLight
import org.mr0btain.ticketman.ui.theme.onSurfaceVariantLightHighContrast
import org.mr0btain.ticketman.ui.theme.onSurfaceVariantLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onTertiaryContainerDark
import org.mr0btain.ticketman.ui.theme.onTertiaryContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onTertiaryContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onTertiaryContainerLight
import org.mr0btain.ticketman.ui.theme.onTertiaryContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.onTertiaryContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.onTertiaryDark
import org.mr0btain.ticketman.ui.theme.onTertiaryDarkHighContrast
import org.mr0btain.ticketman.ui.theme.onTertiaryDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.onTertiaryLight
import org.mr0btain.ticketman.ui.theme.onTertiaryLightHighContrast
import org.mr0btain.ticketman.ui.theme.onTertiaryLightMediumContrast
import org.mr0btain.ticketman.ui.theme.outlineDark
import org.mr0btain.ticketman.ui.theme.outlineDarkHighContrast
import org.mr0btain.ticketman.ui.theme.outlineDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.outlineLight
import org.mr0btain.ticketman.ui.theme.outlineLightHighContrast
import org.mr0btain.ticketman.ui.theme.outlineLightMediumContrast
import org.mr0btain.ticketman.ui.theme.outlineVariantDark
import org.mr0btain.ticketman.ui.theme.outlineVariantDarkHighContrast
import org.mr0btain.ticketman.ui.theme.outlineVariantDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.outlineVariantLight
import org.mr0btain.ticketman.ui.theme.outlineVariantLightHighContrast
import org.mr0btain.ticketman.ui.theme.outlineVariantLightMediumContrast
import org.mr0btain.ticketman.ui.theme.primaryContainerDark
import org.mr0btain.ticketman.ui.theme.primaryContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.primaryContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.primaryContainerLight
import org.mr0btain.ticketman.ui.theme.primaryContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.primaryContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.primaryDark
import org.mr0btain.ticketman.ui.theme.primaryDarkHighContrast
import org.mr0btain.ticketman.ui.theme.primaryDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.primaryLight
import org.mr0btain.ticketman.ui.theme.primaryLightHighContrast
import org.mr0btain.ticketman.ui.theme.primaryLightMediumContrast
import org.mr0btain.ticketman.ui.theme.scrimDark
import org.mr0btain.ticketman.ui.theme.scrimDarkHighContrast
import org.mr0btain.ticketman.ui.theme.scrimDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.scrimLight
import org.mr0btain.ticketman.ui.theme.scrimLightHighContrast
import org.mr0btain.ticketman.ui.theme.scrimLightMediumContrast
import org.mr0btain.ticketman.ui.theme.secondaryContainerDark
import org.mr0btain.ticketman.ui.theme.secondaryContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.secondaryContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.secondaryContainerLight
import org.mr0btain.ticketman.ui.theme.secondaryContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.secondaryContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.secondaryDark
import org.mr0btain.ticketman.ui.theme.secondaryDarkHighContrast
import org.mr0btain.ticketman.ui.theme.secondaryDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.secondaryLight
import org.mr0btain.ticketman.ui.theme.secondaryLightHighContrast
import org.mr0btain.ticketman.ui.theme.secondaryLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceBrightDark
import org.mr0btain.ticketman.ui.theme.surfaceBrightDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceBrightDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceBrightLight
import org.mr0btain.ticketman.ui.theme.surfaceBrightLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceBrightLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerDark
import org.mr0btain.ticketman.ui.theme.surfaceContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighDark
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighLight
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighestDark
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighestDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighestDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighestLight
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighestLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerHighestLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLight
import org.mr0btain.ticketman.ui.theme.surfaceContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowDark
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowLight
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowestDark
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowestDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowestDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowestLight
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowestLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceContainerLowestLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceDark
import org.mr0btain.ticketman.ui.theme.surfaceDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceDimDark
import org.mr0btain.ticketman.ui.theme.surfaceDimDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceDimDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceDimLight
import org.mr0btain.ticketman.ui.theme.surfaceDimLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceDimLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceLight
import org.mr0btain.ticketman.ui.theme.surfaceLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceLightMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceVariantDark
import org.mr0btain.ticketman.ui.theme.surfaceVariantDarkHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceVariantDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.surfaceVariantLight
import org.mr0btain.ticketman.ui.theme.surfaceVariantLightHighContrast
import org.mr0btain.ticketman.ui.theme.surfaceVariantLightMediumContrast
import org.mr0btain.ticketman.ui.theme.tertiaryContainerDark
import org.mr0btain.ticketman.ui.theme.tertiaryContainerDarkHighContrast
import org.mr0btain.ticketman.ui.theme.tertiaryContainerDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.tertiaryContainerLight
import org.mr0btain.ticketman.ui.theme.tertiaryContainerLightHighContrast
import org.mr0btain.ticketman.ui.theme.tertiaryContainerLightMediumContrast
import org.mr0btain.ticketman.ui.theme.tertiaryDark
import org.mr0btain.ticketman.ui.theme.tertiaryDarkHighContrast
import org.mr0btain.ticketman.ui.theme.tertiaryDarkMediumContrast
import org.mr0btain.ticketman.ui.theme.tertiaryLight
import org.mr0btain.ticketman.ui.theme.tertiaryLightHighContrast
import org.mr0btain.ticketman.ui.theme.tertiaryLightMediumContrast

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

private val mediumContrastLightColorScheme = lightColorScheme(
    primary = primaryLightMediumContrast,
    onPrimary = onPrimaryLightMediumContrast,
    primaryContainer = primaryContainerLightMediumContrast,
    onPrimaryContainer = onPrimaryContainerLightMediumContrast,
    secondary = secondaryLightMediumContrast,
    onSecondary = onSecondaryLightMediumContrast,
    secondaryContainer = secondaryContainerLightMediumContrast,
    onSecondaryContainer = onSecondaryContainerLightMediumContrast,
    tertiary = tertiaryLightMediumContrast,
    onTertiary = onTertiaryLightMediumContrast,
    tertiaryContainer = tertiaryContainerLightMediumContrast,
    onTertiaryContainer = onTertiaryContainerLightMediumContrast,
    error = errorLightMediumContrast,
    onError = onErrorLightMediumContrast,
    errorContainer = errorContainerLightMediumContrast,
    onErrorContainer = onErrorContainerLightMediumContrast,
    background = backgroundLightMediumContrast,
    onBackground = onBackgroundLightMediumContrast,
    surface = surfaceLightMediumContrast,
    onSurface = onSurfaceLightMediumContrast,
    surfaceVariant = surfaceVariantLightMediumContrast,
    onSurfaceVariant = onSurfaceVariantLightMediumContrast,
    outline = outlineLightMediumContrast,
    outlineVariant = outlineVariantLightMediumContrast,
    scrim = scrimLightMediumContrast,
    inverseSurface = inverseSurfaceLightMediumContrast,
    inverseOnSurface = inverseOnSurfaceLightMediumContrast,
    inversePrimary = inversePrimaryLightMediumContrast,
    surfaceDim = surfaceDimLightMediumContrast,
    surfaceBright = surfaceBrightLightMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestLightMediumContrast,
    surfaceContainerLow = surfaceContainerLowLightMediumContrast,
    surfaceContainer = surfaceContainerLightMediumContrast,
    surfaceContainerHigh = surfaceContainerHighLightMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestLightMediumContrast,
)

private val highContrastLightColorScheme = lightColorScheme(
    primary = primaryLightHighContrast,
    onPrimary = onPrimaryLightHighContrast,
    primaryContainer = primaryContainerLightHighContrast,
    onPrimaryContainer = onPrimaryContainerLightHighContrast,
    secondary = secondaryLightHighContrast,
    onSecondary = onSecondaryLightHighContrast,
    secondaryContainer = secondaryContainerLightHighContrast,
    onSecondaryContainer = onSecondaryContainerLightHighContrast,
    tertiary = tertiaryLightHighContrast,
    onTertiary = onTertiaryLightHighContrast,
    tertiaryContainer = tertiaryContainerLightHighContrast,
    onTertiaryContainer = onTertiaryContainerLightHighContrast,
    error = errorLightHighContrast,
    onError = onErrorLightHighContrast,
    errorContainer = errorContainerLightHighContrast,
    onErrorContainer = onErrorContainerLightHighContrast,
    background = backgroundLightHighContrast,
    onBackground = onBackgroundLightHighContrast,
    surface = surfaceLightHighContrast,
    onSurface = onSurfaceLightHighContrast,
    surfaceVariant = surfaceVariantLightHighContrast,
    onSurfaceVariant = onSurfaceVariantLightHighContrast,
    outline = outlineLightHighContrast,
    outlineVariant = outlineVariantLightHighContrast,
    scrim = scrimLightHighContrast,
    inverseSurface = inverseSurfaceLightHighContrast,
    inverseOnSurface = inverseOnSurfaceLightHighContrast,
    inversePrimary = inversePrimaryLightHighContrast,
    surfaceDim = surfaceDimLightHighContrast,
    surfaceBright = surfaceBrightLightHighContrast,
    surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
    surfaceContainerLow = surfaceContainerLowLightHighContrast,
    surfaceContainer = surfaceContainerLightHighContrast,
    surfaceContainerHigh = surfaceContainerHighLightHighContrast,
    surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
)

private val mediumContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkMediumContrast,
    onPrimary = onPrimaryDarkMediumContrast,
    primaryContainer = primaryContainerDarkMediumContrast,
    onPrimaryContainer = onPrimaryContainerDarkMediumContrast,
    secondary = secondaryDarkMediumContrast,
    onSecondary = onSecondaryDarkMediumContrast,
    secondaryContainer = secondaryContainerDarkMediumContrast,
    onSecondaryContainer = onSecondaryContainerDarkMediumContrast,
    tertiary = tertiaryDarkMediumContrast,
    onTertiary = onTertiaryDarkMediumContrast,
    tertiaryContainer = tertiaryContainerDarkMediumContrast,
    onTertiaryContainer = onTertiaryContainerDarkMediumContrast,
    error = errorDarkMediumContrast,
    onError = onErrorDarkMediumContrast,
    errorContainer = errorContainerDarkMediumContrast,
    onErrorContainer = onErrorContainerDarkMediumContrast,
    background = backgroundDarkMediumContrast,
    onBackground = onBackgroundDarkMediumContrast,
    surface = surfaceDarkMediumContrast,
    onSurface = onSurfaceDarkMediumContrast,
    surfaceVariant = surfaceVariantDarkMediumContrast,
    onSurfaceVariant = onSurfaceVariantDarkMediumContrast,
    outline = outlineDarkMediumContrast,
    outlineVariant = outlineVariantDarkMediumContrast,
    scrim = scrimDarkMediumContrast,
    inverseSurface = inverseSurfaceDarkMediumContrast,
    inverseOnSurface = inverseOnSurfaceDarkMediumContrast,
    inversePrimary = inversePrimaryDarkMediumContrast,
    surfaceDim = surfaceDimDarkMediumContrast,
    surfaceBright = surfaceBrightDarkMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkMediumContrast,
    surfaceContainerLow = surfaceContainerLowDarkMediumContrast,
    surfaceContainer = surfaceContainerDarkMediumContrast,
    surfaceContainerHigh = surfaceContainerHighDarkMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkMediumContrast,
)

private val highContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkHighContrast,
    onPrimary = onPrimaryDarkHighContrast,
    primaryContainer = primaryContainerDarkHighContrast,
    onPrimaryContainer = onPrimaryContainerDarkHighContrast,
    secondary = secondaryDarkHighContrast,
    onSecondary = onSecondaryDarkHighContrast,
    secondaryContainer = secondaryContainerDarkHighContrast,
    onSecondaryContainer = onSecondaryContainerDarkHighContrast,
    tertiary = tertiaryDarkHighContrast,
    onTertiary = onTertiaryDarkHighContrast,
    tertiaryContainer = tertiaryContainerDarkHighContrast,
    onTertiaryContainer = onTertiaryContainerDarkHighContrast,
    error = errorDarkHighContrast,
    onError = onErrorDarkHighContrast,
    errorContainer = errorContainerDarkHighContrast,
    onErrorContainer = onErrorContainerDarkHighContrast,
    background = backgroundDarkHighContrast,
    onBackground = onBackgroundDarkHighContrast,
    surface = surfaceDarkHighContrast,
    onSurface = onSurfaceDarkHighContrast,
    surfaceVariant = surfaceVariantDarkHighContrast,
    onSurfaceVariant = onSurfaceVariantDarkHighContrast,
    outline = outlineDarkHighContrast,
    outlineVariant = outlineVariantDarkHighContrast,
    scrim = scrimDarkHighContrast,
    inverseSurface = inverseSurfaceDarkHighContrast,
    inverseOnSurface = inverseOnSurfaceDarkHighContrast,
    inversePrimary = inversePrimaryDarkHighContrast,
    surfaceDim = surfaceDimDarkHighContrast,
    surfaceBright = surfaceBrightDarkHighContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
    surfaceContainerLow = surfaceContainerLowDarkHighContrast,
    surfaceContainer = surfaceContainerDarkHighContrast,
    surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

val unspecified_scheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable() () -> Unit
) {
  val colorScheme = when {
      darkTheme -> darkScheme
      else -> lightScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    content = content
  )
}

