package org.mr0btain.ticketman.ui.kit.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale. Sizes are only 12, 14, 16, 20 and 28, plus one hero size
 * @param numeric Monospace, for ticket numbers, money, PINs and SKUs so columns line up
 * @param numericHero A number read out across the counter, like a new ticket number or change due
 */
@Immutable
data class TmTypography(
    val display: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val bodyLarge: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val numeric: TextStyle,
    val numericLarge: TextStyle,
    val numericDisplay: TextStyle,
    val numericHero: TextStyle
) {
    /** Material styles made from ours, so Material text and components use the same scale */
    fun toMaterial(): Typography = Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = display,
        headlineMedium = title,
        headlineSmall = title,
        titleLarge = title,
        titleMedium = subtitle,
        titleSmall = label.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = label,
        labelMedium = caption.copy(fontWeight = FontWeight.Medium),
        labelSmall = caption.copy(fontWeight = FontWeight.Medium)
    )

    companion object {
        fun build(text: FontFamily, mono: FontFamily): TmTypography = TmTypography(
            display = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
            title = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
            subtitle = TextStyle(fontFamily = text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            body = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            label = TextStyle(fontFamily = text, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
            caption = TextStyle(fontFamily = text, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
            numeric = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            numericLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
            numericDisplay = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
            numericHero = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 64.sp)
        )
    }
}
