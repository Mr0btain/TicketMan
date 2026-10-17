package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.counter.TicketSummary
import org.mr0btain.ticketman.server.response.CounterQueueResponse
import org.mr0btain.ticketman.ui.composables.CustomSurface
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.screens.counter.components.TicketSummaryRow
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
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
    CustomSurface(
        modifier = modifier.padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Queue", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

            if (queue == null) {
                EmptyMessage("Queue isn't loaded")
                return@Column
            }

            QueueSection("Ready for Pickup", queue.readyForPickup, CustomMaterialColors.success, onTicketClick)
            QueueSection("Overdue", queue.overdue, MaterialTheme.colorScheme.error, onTicketClick)
            QueueSection("Due Today", queue.dueToday, CustomMaterialColors.inProgress, onTicketClick)
            QueueSection("Just Checked In", queue.recentlyCheckedIn, Color.Unspecified, onTicketClick)
        }
    }
}

@Composable
private fun QueueSection(
    title: String,
    tickets: List<TicketSummary>,
    color: Color,
    onTicketClick: (Uuid) -> Unit
) {
    Text(
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        text = "$title (${tickets.size})",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )

    HorizontalDivider()

    if (tickets.isEmpty()) {
        Text(modifier = Modifier.padding(6.dp), text = "None", fontSize = 12.sp)
    }

    tickets.forEach { summary ->
        TicketSummaryRow(summary, onClick = { onTicketClick(summary.ticket.identifier) })
    }
}
