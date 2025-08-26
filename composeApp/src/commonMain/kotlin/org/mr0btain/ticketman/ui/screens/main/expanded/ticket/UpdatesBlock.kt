package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.util.toImageBitmap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.utils.encodeToBase64
import org.mr0btain.ticketman.data.shared.formatTime
import org.mr0btain.ticketman.data.ticket.TicketUpdate
import org.mr0btain.ticketman.ui.composables.image.ImagePager
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit
import ticketman.composeapp.generated.resources.plus_circle

@Composable
fun UpdatesBlock(
    label: String = "Updates",
    updates: List<TicketUpdate>,
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
                    text = update.note.text,
                    fontSize = textSize
                )

                if (update.photos.isNotEmpty()) {
                    Spacer(Modifier.padding(2.dp))

                    ImagePager(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        imageData = update.photos,
                        imageMaxWidth = 300.dp,
                        imageMaxHeight = 200.dp
                    )
                }

                Spacer(Modifier.padding(2.dp))
            }
        }
    }
}

@Composable
fun EditableUpdatesBlock(
    updates: List<TicketUpdate>,
    textSize: TextUnit = 16.sp,
    onEditClick: (TicketUpdate) -> Unit
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
                        text = update.note.text,
                        fontSize = textSize
                    )

                    if (update.photos.isNotEmpty()) {
                        Spacer(Modifier.padding(2.dp))

                        ImagePager(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            imageData = update.photos,
                            imageMaxWidth = 300.dp,
                            imageMaxHeight = 200.dp
                        )
                    }

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