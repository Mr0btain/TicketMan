package org.mr0btain.ticketman.ui.composables.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.composables.TableStatusItem
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import java.util.*

// TODO: Add time to this
@Composable
fun SmallTicketList(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    tickets: List<Ticket>,
    onClick: (UUID) -> Unit
) {
    PlatformLazyColumn(
        modifier = Modifier.padding(4.dp),
        isVisible = tickets.isNotEmpty()
    ) {
        items(tickets.reversed()) { ticket ->
            Card(
                modifier = Modifier.padding(4.dp),
                onClick = { onClick(ticket.identifier) },
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
                            text = ticket.device.toString(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        TableStatusItem(
                            status = ticket.status,
                            isDarkMode = isDarkMode
                        )
                    }

                    // TODO: Maybe add this back in somehow?? not sure if we really do need it
                    /*
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
                     */
                }
            }
        }
    }
}