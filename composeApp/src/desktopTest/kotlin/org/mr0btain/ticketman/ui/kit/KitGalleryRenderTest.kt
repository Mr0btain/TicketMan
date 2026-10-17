package org.mr0btain.ticketman.ui.kit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.gallery.KitGallery
import org.mr0btain.ticketman.ui.kit.theme.TmFont
import org.mr0btain.ticketman.ui.kit.theme.TmFonts
import org.mr0btain.ticketman.ui.kit.theme.TmPalette
import org.mr0btain.ticketman.ui.kit.theme.TmPalettes
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.settings.components.AppearanceSettings
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws the component gallery to PNGs in build/kit-gallery, one per theme, mode and font,
 * so changes to the kit can be looked over without running the app
 */
class KitGalleryRenderTest {
    private val outputDir = File("build/kit-gallery").apply { mkdirs() }

    private fun render(palette: TmPalette, font: TmFont, dark: Boolean, reduceTransparency: Boolean = false, width: Int = WIDTH): File {
        val name = buildString {
            append("${palette.id}-${if (dark) "dark" else "light"}-${font.id}")
            if (reduceTransparency) append("-solid")
            if (width != WIDTH) append("-$width")
        }

        return renderToFile(name, width, HEIGHT) {
            TmTheme(palette = palette, font = font, darkTheme = dark, reduceTransparency = reduceTransparency) {
                KitGallery(
                    palette = palette,
                    onPaletteChange = {},
                    font = font,
                    onFontChange = {},
                    darkTheme = dark,
                    onDarkThemeChange = {},
                    reduceTransparency = reduceTransparency,
                    onReduceTransparencyChange = {}
                )
            }
        }
    }

    private fun renderToFile(name: String, width: Int, height: Int, content: @Composable () -> Unit): File {
        val bytes = ImageComposeScene(width, height, Density(1f), content = content).use { scene ->
            // Fonts load in the background, so draw a few frames before keeping one
            var frame = scene.render(0)
            repeat(FRAMES) { index ->
                Thread.sleep(FRAME_WAIT_MS)
                frame = scene.render((index + 1) * FRAME_NANOS)
            }
            frame.encodeToData(EncodedImageFormat.PNG)!!.bytes
        }

        return File(outputDir, "$name.png").apply { writeBytes(bytes) }
    }

    @Test
    fun everyThemeInBothModes() {
        TmPalettes.all.forEach { palette ->
            listOf(true, false).forEach { dark ->
                val file = render(palette, TmFonts.default, dark)
                assertTrue(file.length() > 0, "${file.name} should have been drawn")
            }
        }
    }

    @Test
    fun everyFont() {
        TmFonts.all.forEach { font ->
            val file = render(TmPalettes.default, font, dark = true)
            assertTrue(file.length() > 0, "${file.name} should have been drawn")
        }
    }

    @Test
    fun appearanceSettings() {
        listOf(true, false).forEach { dark ->
            val file = renderToFile("settings-appearance-${if (dark) "dark" else "light"}", 900, 1100) {
                TmTheme(darkTheme = dark) {
                    TmBackdrop(Modifier.fillMaxSize()) {
                        AppearanceSettings(
                            modifier = Modifier.padding(24.dp),
                            palette = TmPalettes.default,
                            onPaletteChange = {},
                            darkTheme = dark,
                            onDarkThemeChange = {},
                            font = TmFonts.default,
                            onFontChange = {},
                            reduceTransparency = false,
                            onReduceTransparencyChange = {},
                            onOpenGallery = {}
                        )
                    }
                }
            }
            assertTrue(file.length() > 0, "${file.name} should have been drawn")
        }
    }

    @Test
    fun reducedTransparencyAndNarrow() {
        assertTrue(render(TmPalettes.default, TmFonts.default, dark = true, reduceTransparency = true).length() > 0)
        assertTrue(render(TmPalettes.default, TmFonts.default, dark = true, width = NARROW_WIDTH).length() > 0)
    }

    companion object {
        private const val WIDTH = 1440
        private const val NARROW_WIDTH = 720
        private const val HEIGHT = 3900
        private const val FRAMES = 8
        private const val FRAME_WAIT_MS = 60L
        private const val FRAME_NANOS = 16_000_000L
    }
}
