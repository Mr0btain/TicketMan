package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.data.formatMoney
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.utils.formatDayInSentence
import org.mr0btain.ticketman.server.response.CheckInResponse
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.data.TmTicketIdText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Shown right after a check in, with the number big enough to read out to the customer
 * @param actions Room for more buttons, like printing the claim slip
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckedInView(
    result: CheckInResponse,
    onOpenTicket: () -> Unit,
    onCheckInAnother: () -> Unit,
    onDone: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val ticket = result.ticket
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically)
    ) {
        TmBadge(text = "Checked in", tone = colors.success)

        // The one place a ticket number is read out loud, so it's as big as the screen allows
        TmTicketIdText(
            number = ticket.label,
            style = TmTheme.typography.numericHero
        )

        Text(text = ticket.device.toString(), style = TmTheme.typography.title, color = colors.text)

        // The rest of the visit's devices, each with its own number to read out
        result.linked.forEach { linked ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                TmTicketIdText(number = linked.label, style = TmTheme.typography.numericDisplay)
                Text(text = linked.device.toString(), style = TmTheme.typography.title, color = colors.text)
            }
        }

        val details = listOfNotNull(
            ticket.promisedAt?.let { "Promised ${formatDayInSentence(it)}" },
            result.invoice?.let { "Deposit taken ${formatMoney(it.paidCents)}" }
        )
        if (details.isNotEmpty()) {
            Text(text = details.joinToString(". "), style = TmTheme.typography.bodyLarge, color = colors.textMuted)
        }

        Spacer(Modifier.height(spacing.lg))

        // Wraps on a phone, where the buttons don't fit side by side
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            actions()
            TmButton(text = "Open ticket", onClick = onOpenTicket, variant = TmButtonVariant.SECONDARY, size = TmControlSize.LARGE)
            TmButton(text = "Check in another", onClick = onCheckInAnother, variant = TmButtonVariant.SECONDARY, size = TmControlSize.LARGE)
            TmButton(text = "Done", onClick = onDone, size = TmControlSize.LARGE)
        }
    }
}
