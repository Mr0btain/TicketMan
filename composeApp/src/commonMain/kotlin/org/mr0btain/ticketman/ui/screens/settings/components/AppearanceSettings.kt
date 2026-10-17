package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.containers.TmSwitchRow
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmFont
import org.mr0btain.ticketman.ui.kit.theme.TmFonts
import org.mr0btain.ticketman.ui.kit.theme.TmPalette
import org.mr0btain.ticketman.ui.kit.theme.TmPalettes
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_right
import ticketman.composeapp.generated.resources.droplet
import ticketman.composeapp.generated.resources.layers

/**
 * Theme, mode, font and transparency pickers for the settings screen
 * @param onOpenGallery Opens the component gallery, null hides the row
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettings(
    palette: TmPalette,
    onPaletteChange: (TmPalette) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    font: TmFont,
    onFontChange: (TmFont) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparencyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onOpenGallery: (() -> Unit)? = null
) {
    val spacing = TmTheme.spacing

    TmPanel(modifier = modifier.fillMaxWidth(), title = "Appearance", subtitle = "Only changes this device") {
        PickerLabel("Theme")

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            TmPalettes.all.forEach { option ->
                ThemeCard(
                    palette = option,
                    darkTheme = darkTheme,
                    selected = option == palette,
                    onClick = { onPaletteChange(option) }
                )
            }
        }

        TmSegmented(
            label = "Mode",
            options = listOf(true, false),
            selected = darkTheme,
            onSelect = onDarkThemeChange,
            optionLabel = { if (it) "Dark" else "Light" }
        )

        PickerLabel("Font")

        Column {
            TmFonts.all.forEach { option ->
                val family = option.family()

                TmListRow(
                    title = option.name,
                    subtitle = option.description,
                    selected = option == font,
                    onClick = { onFontChange(option) },
                    leading = {
                        Text(
                            modifier = Modifier.width(32.dp),
                            text = "Ag",
                            style = TmTheme.typography.title.copy(fontFamily = family),
                            color = TmTheme.colors.text
                        )
                    }
                )
            }
        }

        TmDivider()

        TmSwitchRow(
            title = "Reduce transparency",
            subtitle = "Draws floating bars and dialogs as solid, easier to read and lighter on slower machines",
            icon = Res.drawable.droplet,
            checked = reduceTransparency,
            onCheckedChange = onReduceTransparencyChange
        )

        if (onOpenGallery != null) {
            TmListRow(
                title = "Component gallery",
                subtitle = "Every button, field and badge in the current theme",
                icon = Res.drawable.layers,
                onClick = onOpenGallery,
                trailing = {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = painterResource(Res.drawable.chevron_right),
                        contentDescription = null,
                        tint = TmTheme.colors.textMuted
                    )
                }
            )
        }
    }
}

@Composable
private fun PickerLabel(text: String) {
    Text(text = text, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
}

/**
 * A small picture of a theme drawn in its own colors: background, a card with an accent bar, and the status colors
 */
@Composable
private fun ThemeCard(
    palette: TmPalette,
    darkTheme: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val preview = palette.colors(darkTheme)
    val colors = TmTheme.colors
    val shape = TmTheme.shapes.panel
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier
            .widthIn(min = 180.dp)
            .width(200.dp)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent.color else colors.hairline, shape)
            .tmClickable(onClick = onClick, shape = shape, role = Role.RadioButton)
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // Preview in the theme's own colors
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(preview.background, TmTheme.shapes.control)
                .padding(spacing.sm)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(preview.surface, TmTheme.shapes.chip)
                    .border(1.dp, preview.hairline, TmTheme.shapes.chip)
                    .padding(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.width(56.dp).height(6.dp).background(preview.text.copy(alpha = 0.8f), TmTheme.shapes.pill))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Box(Modifier.width(36.dp).height(14.dp).background(preview.accent.color, TmTheme.shapes.chip))
                    TmDot(preview.warning.color, Modifier.padding(top = 4.dp))
                    TmDot(preview.danger.color, Modifier.padding(top = 4.dp))
                    TmDot(preview.success.color, Modifier.padding(top = 4.dp))
                }
            }
        }

        Column(Modifier.padding(horizontal = spacing.xs)) {
            Text(text = palette.name, style = TmTheme.typography.label, color = colors.text)
            Text(text = palette.description, style = TmTheme.typography.caption, color = colors.textMuted)
        }
    }
}
