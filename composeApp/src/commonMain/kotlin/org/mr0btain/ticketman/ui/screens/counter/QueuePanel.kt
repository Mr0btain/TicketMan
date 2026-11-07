package org.mr0btain.ticketman.ui.screens.counter

import ticketman.composeapp.generated.resources.message_square
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.uikit.containers.TmListRow
import org.mr0btain.ticketman.data.notify.InboundMessage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.uikit.containers.TmDialog
import org.mr0btain.uikit.containers.TmPaneHeader
import org.mr0btain.uikit.feedback.TmDot
import org.mr0btain.uikit.feedback.TmLoading
import org.mr0btain.uikit.foundation.TmDivider
import org.mr0btain.uikit.foundation.TmSurface
import org.mr0btain.uikit.theme.TmTheme
import org.mr0btain.uikit.theme.TmTone
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
    onTicketClick: (Uuid) -> Unit,
    texts: List<InboundMessage> = emptyList(),
    onTextClick: (InboundMessage) -> Unit = {}
) {
    TmSurface(modifier = modifier) {
        Column {
            TmPaneHeader(title = "Queue", icon = Res.drawable.inbox)
            TmDivider()

            if (queue == null) {
                TmLoading(modifier = Modifier.fillMaxWidth(), text = "Loading the queue")
                return@Column
            }

            QueueSections(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(TmTheme.spacing.sm),
                queue = queue,
                onTicketClick = onTicketClick,
                texts = texts,
                onTextClick = onTextClick
            )
        }
    }
}

/**
 * The queue in a dialog, for windows too narrow to keep [QueuePanel] beside everything else
 */
@Composable
fun QueueDialog(
    queue: CounterQueueResponse?,
    onTicketClick: (Uuid) -> Unit,
    onDismissRequest: () -> Unit,
    texts: List<InboundMessage> = emptyList(),
    onTextClick: (InboundMessage) -> Unit = {}
) {
    TmDialog(title = "Queue", onDismissRequest = onDismissRequest) {
        if (queue == null) {
            TmLoading(modifier = Modifier.fillMaxWidth(), text = "Loading the queue")
        } else {
            QueueSections(queue = queue, onTicketClick = onTicketClick, texts = texts, onTextClick = onTextClick)
        }
    }
}

/**
 * Each part of the queue under its heading, unread texts first when there are any. Doesn't scroll, the panel or
 * dialog it's in does
 */
@Composable
private fun QueueSections(
    queue: CounterQueueResponse,
    onTicketClick: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    texts: List<InboundMessage> = emptyList(),
    onTextClick: (InboundMessage) -> Unit = {}
) {
    val colors = TmTheme.colors

    Column(modifier) {
        if (texts.isNotEmpty()) TextsSection(texts, onTextClick)
        QueueSection("Ready for pickup", queue.readyForPickup, colors.success, onTicketClick)
        QueueSection("Overdue", queue.overdue, colors.danger, onTicketClick)
        QueueSection("Due today", queue.dueToday, colors.warning, onTicketClick)
        QueueSection("Just checked in", queue.recentlyCheckedIn, colors.neutral, onTicketClick)
        // Only when there are some, most shops won't have abandoned devices
        if (queue.abandoned.isNotEmpty()) QueueSection("Abandoned", queue.abandoned, colors.danger, onTicketClick)
    }
}

/** Texts customers sent that nobody's read, each opening its ticket or customer */
@Composable
private fun TextsSection(texts: List<InboundMessage>, onTextClick: (InboundMessage) -> Unit) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Row(
        modifier = Modifier.padding(start = spacing.md, end = spacing.md, top = spacing.md, bottom = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TmDot(colors.accent.color)
        Text(modifier = Modifier.weight(1f), text = "New texts", style = TmTheme.typography.label, color = colors.text)
        Text(text = texts.size.toString(), style = TmTheme.typography.numeric, color = colors.textMuted)
    }

    texts.forEach { message ->
        TmListRow(
            title = listOf(message.customerName.ifBlank { message.sender }, message.ticketLabel).filter { it.isNotBlank() }.joinToString(" · "),
            subtitle = "${message.body.replace('\n', ' ').take(80)} · ${formatTimeAgo(message.receivedAt)}",
            icon = Res.drawable.message_square,
            onClick = { onTextClick(message) }
        )
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
