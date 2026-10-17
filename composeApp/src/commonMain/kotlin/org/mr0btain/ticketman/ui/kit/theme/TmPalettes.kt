package org.mr0btain.ticketman.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A theme the user can pick, with a dark and a light version
 * @param id Saved in settings, so don't change it once released
 * @param description Shown under the name in the theme picker
 */
@Immutable
data class TmPalette(
    val id: String,
    val name: String,
    val description: String,
    val dark: TmColors,
    val light: TmColors
) {
    fun colors(darkTheme: Boolean): TmColors = if (darkTheme) dark else light
}

/**
 * Every theme the user can pick. To add one, make a [TmPalette] below and put it in [all]
 */
object TmPalettes {
    private const val DARK_SOFT = 0.16f
    private const val LIGHT_SOFT = 0.12f

    /** Graphite with a soft blue accent */
    val Bench = TmPalette(
        id = "bench",
        name = "Bench",
        description = "Graphite with a soft blue accent",
        dark = TmColors(
            isDark = true,
            background = Color(0xFF14171B),
            glow = Color(0xFF7CC4FF).copy(alpha = 0.16f),
            glass = Color(0xFF1E232A).copy(alpha = 0.72f),
            surface = Color(0xFF1C2127),
            surfaceRaised = Color(0xFF252B33),
            surfaceSunken = Color(0xFF171B20),
            hairline = Color.White.copy(alpha = 0.08f),
            outline = Color.White.copy(alpha = 0.18f),
            text = Color(0xFFE6EAF0),
            textMuted = Color(0xFF98A2B0),
            textFaint = Color(0xFF5F6873),
            accent = TmTone.of(Color(0xFF7CC4FF), Color(0xFF0B2236), DARK_SOFT),
            success = TmTone.of(Color(0xFF6FD39A), Color(0xFF0A2A18), DARK_SOFT),
            warning = TmTone.of(Color(0xFFF2B45A), Color(0xFF2E1D00), DARK_SOFT),
            danger = TmTone.of(Color(0xFFF07A6A), Color(0xFF3A0E08), DARK_SOFT),
            neutral = TmTone.of(Color(0xFF8A94A6), Color(0xFF161A20), DARK_SOFT)
        ),
        light = TmColors(
            isDark = false,
            background = Color(0xFFEEF1F5),
            glow = Color(0xFF2F6FA8).copy(alpha = 0.10f),
            glass = Color(0xFFFFFFFF).copy(alpha = 0.70f),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFF5F7FA),
            surfaceSunken = Color(0xFFF0F3F6),
            hairline = Color(0xFF1B2A3A).copy(alpha = 0.10f),
            outline = Color(0xFF1B2A3A).copy(alpha = 0.24f),
            text = Color(0xFF1A1F26),
            textMuted = Color(0xFF56606C),
            textFaint = Color(0xFF9AA3AD),
            accent = TmTone.of(Color(0xFF2F6FA8), Color(0xFFFFFFFF), LIGHT_SOFT),
            success = TmTone.of(Color(0xFF1E7A4A), Color(0xFFFFFFFF), LIGHT_SOFT),
            warning = TmTone.of(Color(0xFF9A5B00), Color(0xFFFFFFFF), LIGHT_SOFT),
            danger = TmTone.of(Color(0xFFB3392B), Color(0xFFFFFFFF), LIGHT_SOFT),
            neutral = TmTone.of(Color(0xFF5B6676), Color(0xFFFFFFFF), LIGHT_SOFT)
        )
    )

    /** Warm dark brown with a copper accent */
    val Copper = TmPalette(
        id = "copper",
        name = "Copper",
        description = "Warm brown with a copper accent",
        dark = TmColors(
            isDark = true,
            background = Color(0xFF18130F),
            glow = Color(0xFFE39B6B).copy(alpha = 0.14f),
            glass = Color(0xFF231B15).copy(alpha = 0.72f),
            surface = Color(0xFF211A15),
            surfaceRaised = Color(0xFF2B231C),
            surfaceSunken = Color(0xFF15100D),
            hairline = Color(0xFFFFE8D6).copy(alpha = 0.08f),
            outline = Color(0xFFFFE8D6).copy(alpha = 0.18f),
            text = Color(0xFFEFE7DF),
            textMuted = Color(0xFFB0A091),
            textFaint = Color(0xFF6E6156),
            accent = TmTone.of(Color(0xFFE39B6B), Color(0xFF2B1406), DARK_SOFT),
            success = TmTone.of(Color(0xFF8BCF8F), Color(0xFF0F2A11), DARK_SOFT),
            warning = TmTone.of(Color(0xFFF2C35A), Color(0xFF2E2200), DARK_SOFT),
            danger = TmTone.of(Color(0xFFF2676A), Color(0xFF3A0A0B), DARK_SOFT),
            neutral = TmTone.of(Color(0xFFA39686), Color(0xFF1C1611), DARK_SOFT)
        ),
        light = TmColors(
            isDark = false,
            background = Color(0xFFF4EFEA),
            glow = Color(0xFF9A5530).copy(alpha = 0.10f),
            glass = Color(0xFFFFFDFB).copy(alpha = 0.72f),
            surface = Color(0xFFFFFDFB),
            surfaceRaised = Color(0xFFF9F4EF),
            surfaceSunken = Color(0xFFF2ECE6),
            hairline = Color(0xFF3A2414).copy(alpha = 0.10f),
            outline = Color(0xFF3A2414).copy(alpha = 0.24f),
            text = Color(0xFF2A211A),
            textMuted = Color(0xFF6B5D51),
            textFaint = Color(0xFFA8998C),
            accent = TmTone.of(Color(0xFF9A5530), Color(0xFFFFFFFF), LIGHT_SOFT),
            success = TmTone.of(Color(0xFF2E7D3E), Color(0xFFFFFFFF), LIGHT_SOFT),
            warning = TmTone.of(Color(0xFF8A6100), Color(0xFFFFFFFF), LIGHT_SOFT),
            danger = TmTone.of(Color(0xFFB3262E), Color(0xFFFFFFFF), LIGHT_SOFT),
            neutral = TmTone.of(Color(0xFF6E6257), Color(0xFFFFFFFF), LIGHT_SOFT)
        )
    )

    /** Neutral greys, color only where it means something */
    val Mono = TmPalette(
        id = "mono",
        name = "Mono",
        description = "Neutral greys, color for status",
        dark = TmColors(
            isDark = true,
            background = Color(0xFF17181A),
            glow = Color.White.copy(alpha = 0.05f),
            glass = Color(0xFF202124).copy(alpha = 0.74f),
            surface = Color(0xFF1F2023),
            surfaceRaised = Color(0xFF292A2E),
            surfaceSunken = Color(0xFF131416),
            hairline = Color.White.copy(alpha = 0.09f),
            outline = Color.White.copy(alpha = 0.22f),
            text = Color(0xFFECECEE),
            textMuted = Color(0xFFA3A4A9),
            textFaint = Color(0xFF66676C),
            accent = TmTone.of(Color(0xFFE6E7EA), Color(0xFF17181A), 0.12f),
            success = TmTone.of(Color(0xFF7FD3A0), Color(0xFF0B2A17), DARK_SOFT),
            warning = TmTone.of(Color(0xFFE8BC6A), Color(0xFF2E2000), DARK_SOFT),
            danger = TmTone.of(Color(0xFFF08578), Color(0xFF3A0E08), DARK_SOFT),
            neutral = TmTone.of(Color(0xFF9A9BA0), Color(0xFF17181A), DARK_SOFT)
        ),
        light = TmColors(
            isDark = false,
            background = Color(0xFFF2F2F3),
            glow = Color.Black.copy(alpha = 0.04f),
            glass = Color(0xFFFFFFFF).copy(alpha = 0.72f),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFF6F6F7),
            surfaceSunken = Color(0xFFEEEEF0),
            hairline = Color.Black.copy(alpha = 0.10f),
            outline = Color.Black.copy(alpha = 0.26f),
            text = Color(0xFF18191B),
            textMuted = Color(0xFF55575C),
            textFaint = Color(0xFF9C9EA3),
            accent = TmTone.of(Color(0xFF20242A), Color(0xFFFFFFFF), 0.08f),
            success = TmTone.of(Color(0xFF1E7A4A), Color(0xFFFFFFFF), LIGHT_SOFT),
            warning = TmTone.of(Color(0xFF9A5B00), Color(0xFFFFFFFF), LIGHT_SOFT),
            danger = TmTone.of(Color(0xFFB3392B), Color(0xFFFFFFFF), LIGHT_SOFT),
            neutral = TmTone.of(Color(0xFF5F6168), Color(0xFFFFFFFF), LIGHT_SOFT)
        )
    )

    val default = Bench

    /** Listed in this order in the theme picker */
    val all: List<TmPalette> = listOf(Bench, Copper, Mono)

    fun byId(id: String): TmPalette = all.firstOrNull { it.id == id } ?: default
}
