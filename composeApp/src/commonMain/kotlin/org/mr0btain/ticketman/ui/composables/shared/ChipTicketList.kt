package org.mr0btain.ticketman.ui.composables.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.data.ticket.TicketData
import java.util.UUID
import kotlin.collections.forEach

@Composable
fun ChipTicketList(
    modifier: Modifier = Modifier,
    tickets: List<UUID>,
    onTicketClick: (UUID) -> Unit,
    onDeleteClick: (UUID) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        tickets.forEach { ticket ->
            CustomInputChip(
                label = ticket.toString(),
                onClick = { onTicketClick(ticket) },
                onDeleteClick = { onDeleteClick(ticket) }
            )

            Spacer(Modifier.padding(4.dp))
        }
    }
}