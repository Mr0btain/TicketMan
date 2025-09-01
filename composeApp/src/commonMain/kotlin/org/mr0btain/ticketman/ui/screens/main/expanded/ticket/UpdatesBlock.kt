package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.shared.formatTime
import org.mr0btain.ticketman.data.ticket.TicketUpdate
import org.mr0btain.ticketman.ui.composables.image.ImagePager

@Composable
fun UpdatesBlock(
    label: String = "Updates",
    updates: List<TicketUpdate>,
    onImageClick: (List<String>) -> Unit,
    labelSize: TextUnit = 12.sp,
    textSize: TextUnit = 16.sp
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Label
        if (label.isNotBlank()) {
            Text(
                modifier = Modifier
                    .padding(2.dp),
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = labelSize
            )

            Spacer(Modifier.padding(2.dp))
        }

        updates.sortedByDescending { it.identifier }.forEach { update ->
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Author
                    Text(
                        text = update.note.author + ": ",
                        fontSize = textSize,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Time
                    Text(
                        text = formatTime(update.note.time).toString(),
                        fontSize = (textSize.value - 4).sp,
                        maxLines = 1
                    )
                }

                // Text
                Text(
                    modifier = Modifier,
                    text = update.note.message,
                    fontSize = textSize
                )

                /*
                if (update.images.isNotEmpty()) {
                    Spacer(Modifier.padding(2.dp))

                    ImagePager(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        imageData = update.images,
                        onImageClick = { onImageClick(update) },
                        imageMaxWidth = 300.dp,
                        imageMaxHeight = 200.dp
                    )
                }
                 */

                Spacer(Modifier.padding(2.dp))
            }
        }
    }
}