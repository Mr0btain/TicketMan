package org.mr0btain.ticketman.ui.composables.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChipList(
    modifier: Modifier = Modifier,
    items: List<String>,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column(
        modifier = modifier
    ) {
        items.forEachIndexed { index, email ->
            CustomChip(
                label = items[index],
                onEditClick =  { onEditClick(email) },
                onDeleteClick =  { onDeleteClick(email) }
            )

            Spacer(Modifier.padding(2.dp))
        }
    }
}