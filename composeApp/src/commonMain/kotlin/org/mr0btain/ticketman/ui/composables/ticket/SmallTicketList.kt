package org.mr0btain.ticketman.ui.composables.ticket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.data.ticket.toNameString
import org.mr0btain.ticketman.ui.composables.TableStatusItem
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tag
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Add time to this
@OptIn(ExperimentalUuidApi::class)
@Composable
fun SmallTicketList(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    ticketData: List<TicketData>,
    onClick: (Uuid) -> Unit
) {
    PlatformLazyColumn(
        modifier = Modifier.padding(4.dp),
        isVisible = ticketData.isNotEmpty()
    ) {
        items(ticketData.reversed()) { data ->
            Card(
                modifier = Modifier.padding(4.dp),
                onClick = { onClick(data.ticket.identifier) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = data.ticket.device.toNameString(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        TableStatusItem(
                            status = data.ticket.status,
                            isDarkMode = isDarkMode
                        )
                    }

                    Spacer(Modifier.padding(4.dp))

                    // Notes section
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // We only want a tidbit of notes test from this data, we can get the
                        // images and such when we click on it for more info
                        data.notes.take(3).forEach { (note, _) ->
                            Row(
                                modifier = Modifier.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = note.author + ": ",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = note.message,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}