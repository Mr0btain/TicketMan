package org.mr0btain.ticketman.ui.kit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import ticketman.composeapp.generated.resources.*

/**
 * A font the user can pick for the interface. Numbers always use [TmFonts.mono]
 * @param id Saved in settings, so don't change it once released
 * @param faces Font files and their weights, empty uses the platform's default font
 */
@Immutable
class TmFont(
    val id: String,
    val name: String,
    val description: String,
    private val faces: List<Pair<FontResource, FontWeight>>
) {
    @Composable
    fun family(): FontFamily =
        if (faces.isEmpty()) FontFamily.Default
        else FontFamily(faces.map { (resource, weight) -> Font(resource, weight) })
}

/**
 * Every font the user can pick. To add one, put its .ttf files in composeResources/font,
 * make a [TmFont] below and put it in [all]
 */
object TmFonts {
    val Barlow = TmFont(
        id = "barlow",
        name = "Barlow",
        description = "Compact and clear, the default",
        faces = listOf(
            Res.font.barlow_regular to FontWeight.Normal,
            Res.font.barlow_medium to FontWeight.Medium,
            Res.font.barlow_semibold to FontWeight.SemiBold,
            Res.font.barlow_bold to FontWeight.Bold
        )
    )

    val Atkinson = TmFont(
        id = "atkinson",
        name = "Atkinson Hyperlegible",
        description = "Letters that are hard to mix up, good for shared screens",
        faces = listOf(
            Res.font.atkinson_regular to FontWeight.Normal,
            Res.font.atkinson_bold to FontWeight.Bold
        )
    )

    val System = TmFont(
        id = "system",
        name = "System",
        description = "This device's own font",
        faces = emptyList()
    )

    val default = Barlow

    /** Listed in this order in the font picker */
    val all: List<TmFont> = listOf(Barlow, Atkinson, System)

    fun byId(id: String): TmFont = all.firstOrNull { it.id == id } ?: default

    /** Used for ticket and invoice numbers, money, PINs and SKUs, whatever font is picked */
    @Composable
    fun mono(): FontFamily = FontFamily(
        Font(Res.font.jbmono_reg, FontWeight.Normal),
        Font(Res.font.jbmono_medium, FontWeight.Medium),
        Font(Res.font.jbmono_semibold, FontWeight.SemiBold),
        Font(Res.font.jbmono_bold, FontWeight.Bold)
    )
}
