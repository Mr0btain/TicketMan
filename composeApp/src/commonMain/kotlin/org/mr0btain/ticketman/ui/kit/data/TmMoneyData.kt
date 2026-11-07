package org.mr0btain.ticketman.ui.kit.data

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.invoice.TaxRate
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.data.shop.StorageTerms
import org.mr0btain.uikit.theme.TmTheme

/**
 * The shop's currency symbol, put before every amount. Provided from the shop profile in `App`, `$` until it loads
 */
val LocalCurrencySymbol = staticCompositionLocalOf { Money.DEFAULT_SYMBOL }

/**
 * The shop's tax rates now: its main rate and its other classes
 * @param mainBasisPoints The main rate, 825 is 8.25%
 */
data class TmTaxes(
    val mainBasisPoints: Int = 0,
    val classes: List<TaxRate> = emptyList()
) {
    /** What a line is taxed at, by its class */
    fun label(taxable: Boolean, taxClass: String?): String =
        if (!taxable) "No tax" else classes.firstOrNull { it.identifier == taxClass }?.let { "${it.name} ${it.basisPoints / 100.0}%" } ?: "Tax ${mainBasisPoints / 100.0}%"
}

/** The shop's tax rates, provided from the shop profile in `App`, for line editors and previews */
val LocalTaxes = staticCompositionLocalOf { TmTaxes() }

/** The shop's storage terms, provided from the shop profile in `App`, for when a waiting device's fee starts */
val LocalStorageTerms = staticCompositionLocalOf { StorageTerms() }

/** An amount in cents as text, with the shop's currency symbol, for amounts inside a sentence */
@Composable
fun formatMoney(cents: Long): String = Money.format(cents, LocalCurrencySymbol.current)

/**
 * Money from cents, in the monospace face so amounts line up
 * @param signed Shows negative amounts in the danger color
 */
@Composable
fun TmMoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = TmTheme.typography.numeric,
    color: Color = Color.Unspecified,
    signed: Boolean = false
) {
    val colors = TmTheme.colors
    Text(
        modifier = modifier,
        text = formatMoney(cents),
        style = style,
        color = when {
            color.isSpecified -> color
            signed && cents < 0 -> colors.danger.color
            else -> colors.text
        },
        maxLines = 1
    )
}

/**
 * Ticket or invoice number like 26-00000042, monospace so they're easy to read back. Shrinks to fit rather than
 * losing digits off the end, which the big styles would on a phone
 */
@Composable
fun TmTicketIdText(
    number: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TmTheme.typography.numeric,
    color: Color = Color.Unspecified
) {
    BasicText(
        modifier = modifier,
        text = number,
        style = style.copy(color = if (color.isSpecified) color else TmTheme.colors.accent.color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize)
    )
}
