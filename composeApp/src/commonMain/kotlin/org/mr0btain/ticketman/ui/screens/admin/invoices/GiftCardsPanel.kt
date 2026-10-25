package org.mr0btain.ticketman.ui.screens.admin.invoices

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatDate
import org.mr0btain.ticketman.data.giftcard.GiftCard
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmAdjustBalanceDialog
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.gift

private val CARD_COLUMNS = listOf(
    TmColumn("Code", weight = 1f),
    TmColumn("Sold", width = 120.dp),
    TmColumn("Loaded", width = 104.dp, end = true),
    TmColumn("Balance", width = 104.dp, end = true),
    TmColumn("", width = 96.dp, end = true)
)

/**
 * Every gift card, newest first, each with what's on it, and a way to correct one
 * @param onAdjust Changes a card's balance by an amount with a reason, calls back with what went wrong or null
 */
@Composable
fun GiftCardsPanel(
    cards: List<GiftCard>,
    onAdjust: (GiftCard, Long, String, (String?) -> Unit) -> Unit
) {
    var adjusting by remember { mutableStateOf<GiftCard?>(null) }

    adjusting?.let { card ->
        TmAdjustBalanceDialog(
            title = "Gift card ${card.code}",
            balanceCents = card.balanceCents,
            onSave = { amount, reason, onResult -> onAdjust(card, amount, reason, onResult) },
            onDismissRequest = { adjusting = null }
        )
    }

    TmPanel(title = "Gift cards", subtitle = "Sold at the counter or given as refunds", icon = Res.drawable.gift) {
        if (cards.isEmpty()) {
            TmEmptyState(icon = Res.drawable.gift, title = "No gift cards yet", message = "Sell one at the counter with Add gift card.")
            return@TmPanel
        }

        Column {
            TmTableHeader(CARD_COLUMNS)

            cards.forEach { card ->
                TmTableRow(CARD_COLUMNS) { column ->
                    when (column) {
                        0 -> Text(card.code, style = TmTheme.typography.numeric, color = TmTheme.colors.text)
                        1 -> Text(formatDate(card.createdAt), style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
                        2 -> TmMoneyText(card.issuedCents, color = TmTheme.colors.textMuted)
                        3 -> TmMoneyText(card.balanceCents)
                        4 -> TmButton(text = "Adjust", onClick = { adjusting = card }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL)
                    }
                }
            }
        }
    }
}
