package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.ui.kit.containers.TmPaneHeader
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.TmLoading
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone
import org.mr0btain.ticketman.ui.screens.counter.components.TicketSummaryRow
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.inbox
import kotlin.uuid.Uuid

/**
 * What needs attention at the counter, kept beside everything else
 */
@Composable
fun QueuePanel(
    modifier: Modifier = Modifier,
    queue: CounterQueueResponse?,
    onTicketClick: (Uuid) -> Unit
) {
    val colors = TmTheme.colors

    TmSurface(modifier = modifier) {
        Column {
            TmPaneHeader(title = "Queue", icon = Res.drawable.inbox)
            TmDivider()

            if (queue == null) {
                TmLoading(modifier = Modifier.fillMaxWidth(), text = "Loading the queue")
                return@Column
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(TmTheme.spacing.sm)
            ) {
                QueueSection("Ready for pickup", queue.readyForPickup, colors.success, onTicketClick)
                QueueSection("Overdue", queue.overdue, colors.danger, onTicketClick)
                QueueSection("Due today", queue.dueToday, colors.warning, onTicketClick)
                QueueSection("Just checked in", queue.recentlyCheckedIn, colors.neutral, onTicketClick)
            }
        }
    }
}

@Composable
private fun QueueSection(
    title: String,
    tickets: List<TicketSummary>,
    tone: TmTone,
    onTicketClick: (Uuid) -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Row(
        modifier = Modifier.padding(start = spacing.md, end = spacing.md, top = spacing.md, bottom = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TmDot(tone.color)
        Text(modifier = Modifier.weight(1f), text = title, style = TmTheme.typography.label, color = colors.text)
        Text(text = tickets.size.toString(), style = TmTheme.typography.numeric, color = colors.textMuted)
    }

    if (tickets.isEmpty()) {
        Text(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
            text = "Nothing here",
            style = TmTheme.typography.caption,
            color = colors.textFaint
        )
    }

    tickets.forEach { summary ->
        TicketSummaryRow(summary, showStatus = false, onClick = { onTicketClick(summary.ticket.identifier) })
    }
}
