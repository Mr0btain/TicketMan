package org.mr0btain.ticketman.ui.kit.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * A number with its label, for overview screens
 * @param attention Draws the number in the danger color, for things someone needs to look at
 */
@Composable
fun TmStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    caption: String? = null,
    attention: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = TmTheme.colors

    TmSurface(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier.padding(TmTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = colors.textMuted
                    )
                }

                Text(
                    text = label,
                    style = TmTheme.typography.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Shrinks long amounts to fit narrow tiles instead of cutting them off
            BasicText(
                text = value,
                style = TmTheme.typography.numericDisplay.copy(color = if (attention) colors.danger.color else colors.text),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = TmTheme.typography.numericDisplay.fontSize)
            )

            if (caption != null) {
                Text(
                    text = caption,
                    style = TmTheme.typography.caption,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Labelled bar whose length is [count] out of [total] */
@Composable
fun TmCountBar(
    label: String,
    count: Long,
    total: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val colors = TmTheme.colors
    val fraction = if (total > 0) (count.toFloat() / total).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TmTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.width(110.dp),
            text = label,
            style = TmTheme.typography.body,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .background(colors.surfaceSunken, TmTheme.shapes.pill)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(if (color.isSpecified) color else colors.accent.color, TmTheme.shapes.pill)
            )
        }

        Text(
            modifier = Modifier.width(48.dp),
            text = count.toString(),
            style = TmTheme.typography.numeric,
            color = colors.text,
            textAlign = TextAlign.End
        )
    }
}

/**
 * The shop's currency symbol, put before every amount. Provided from the shop profile in `App`, `$` until it loads
 */
val LocalCurrencySymbol = staticCompositionLocalOf { Money.DEFAULT_SYMBOL }

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

/**
 * Label on the left, value on the right, for detail panes and receipts.
 * Pass [content] in place of [value] for something other than plain text
 */
@Composable
fun TmKeyValueRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    numeric: Boolean = false,
    content: (@Composable () -> Unit)? = null
) {
    val colors = TmTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TmTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = TmTheme.typography.body,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        when {
            content != null -> content()
            value != null -> Text(
                text = value,
                style = if (numeric) TmTheme.typography.numeric else TmTheme.typography.body,
                color = colors.text,
                textAlign = TextAlign.End
            )
        }
    }
}
