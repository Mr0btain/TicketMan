package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.data.shared.formatTime
import org.mr0btain.ticketman.data.ticket.TicketUpdate
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit

@Composable
fun EditableUpdatesBlock(
    updates: List<TicketUpdate>,
    textSize: TextUnit = 16.sp,
    onEditClick: (TicketUpdate) -> Unit,
    onImageClick: (TicketUpdate) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        updates.sortedByDescending { it.identifier }.forEach { update ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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

                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = { onEditClick(update) }
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(Res.drawable.edit),
                        contentDescription = "Edit Update"
                    )
                }
            }
        }
    }
}