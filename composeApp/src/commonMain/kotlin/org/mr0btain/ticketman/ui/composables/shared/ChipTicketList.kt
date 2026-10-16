package org.mr0btain.ticketman.ui.composables.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.collections.forEach
import kotlin.uuid.Uuid

@Composable
fun ChipTicketList(
    modifier: Modifier = Modifier,
    tickets: List<Uuid>,
    onTicketClick: (Uuid) -> Unit,
    onDeleteClick: (Uuid) -> Unit
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