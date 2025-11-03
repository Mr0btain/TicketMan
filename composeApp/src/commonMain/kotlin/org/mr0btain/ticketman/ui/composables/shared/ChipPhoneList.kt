package org.mr0btain.ticketman.ui.composables.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun ChipPhoneList(
    modifier: Modifier = Modifier,
    items: List<String>,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    Column {
        for (i in 0..(items.size / 3) + 1) {
            val startIndex = i * 3

            // CHeck if there is even items in this row
            if (items.size > startIndex) {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.subList(startIndex, min(startIndex + 3, items.size)).forEach { phoneNumber ->
                        CustomChip(
                            label = phoneNumber,
                            onEditClick = { onEditClick(phoneNumber) },
                            onDeleteClick = { onDeleteClick(phoneNumber) }
                        )

                        Spacer(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}