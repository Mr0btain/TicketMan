package org.mr0btain.ticketman.ui.kit.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.foundation.TmGlassSurface
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.foundation.glassSource
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.selection.TmSwitch
import org.mr0btain.ticketman.ui.kit.theme.TmFont
import org.mr0btain.ticketman.ui.kit.theme.TmFonts
import org.mr0btain.ticketman.ui.kit.theme.TmPalette
import org.mr0btain.ticketman.ui.kit.theme.TmPalettes
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/** Width where the gallery switches to two columns of sections */
private val TWO_COLUMN_WIDTH = 1100.dp

/**
 * Every kit component in every state, with pickers to try each theme and font.
 * Change a component in ui/kit and check it here before using it on a real screen
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitGallery(
    palette: TmPalette,
    onPaletteChange: (TmPalette) -> Unit,
    font: TmFont,
    onFontChange: (TmFont) -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparencyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = TmTheme.spacing
    var headerHeight by remember { mutableIntStateOf(0) }
    val headerHeightDp = with(LocalDensity.current) { headerHeight.toDp() }

    TmBackdrop(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoColumns = maxWidth >= TWO_COLUMN_WIDTH
            val sections = gallerySections()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .glassSource()
                    .verticalScroll(rememberScrollState())
                    .padding(PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.xl))
            ) {
                // Room for the header floating on top, the content scrolls under it
                Spacer(Modifier.height(headerHeightDp + spacing.lg))

                if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                        listOf(0, 1).forEach { column ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                                sections.filterIndexed { index, _ -> index % 2 == column }.forEach { it() }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                        sections.forEach { it() }
                    }
                }
            }

            TmGlassSurface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(spacing.sm)
                    .onSizeChanged { headerHeight = it.height }
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    itemVerticalAlignment = Alignment.Bottom
                ) {
                    Column(Modifier.padding(end = spacing.xl)) {
                        Text("Components", style = TmTheme.typography.title, color = TmTheme.colors.text)
                        Text(
                            "Everything in ui/kit, in the theme picked here",
                            style = TmTheme.typography.caption,
                            color = TmTheme.colors.textMuted
                        )
                    }

                    TmSegmented(
                        label = "Theme",
                        options = TmPalettes.all,
                        selected = palette,
                        onSelect = onPaletteChange,
                        optionLabel = { it.name }
                    )

                    TmSegmented(
                        label = "Font",
                        options = TmFonts.all,
                        selected = font,
                        onSelect = onFontChange,
                        optionLabel = { it.name }
                    )

                    TmSegmented(
                        label = "Mode",
                        options = listOf(true, false),
                        selected = darkTheme,
                        onSelect = onDarkThemeChange,
                        optionLabel = { if (it) "Dark" else "Light" }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text("Reduce transparency", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
                        Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) {
                            TmSwitch(
                                checked = reduceTransparency,
                                onCheckedChange = onReduceTransparencyChange,
                                contentDescription = "Reduce transparency"
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A titled card in the gallery */
@Composable
internal fun GallerySection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    TmPanel(modifier = Modifier.fillMaxWidth(), title = title, subtitle = description, content = content)
}

/** A small caption over a group of samples, naming the state they show */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Specimen(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
        Text(text = label, style = TmTheme.typography.caption, color = TmTheme.colors.textFaint)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}
