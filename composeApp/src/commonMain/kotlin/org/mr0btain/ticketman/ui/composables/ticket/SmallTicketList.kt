package org.mr0btain.ticketman.ui.composables.ticket

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tag
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Add time to this
@OptIn(ExperimentalUuidApi::class)
@Composable
fun SmallTicketList(
    modifier: Modifier = Modifier,
    ticketData: List<TicketData>?,
    scrollBarsOnRight: Boolean,
    isDarkMode: Boolean,
    onClick: (Uuid) -> Unit
) {
    Surface(
        modifier = modifier
            .padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Main Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(Res.drawable.tag),
                    contentDescription = "Ticket List"
                )

                Spacer(Modifier.padding(4.dp))

                Text(
                    text = "Tickets",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            ticketData?.let {
                PlatformColumnScrollable(
                    modifier = Modifier.padding(4.dp),
                    scrollBarOnRight = scrollBarsOnRight,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    isVisible = ticketData.isNotEmpty()
                ) {
                    ticketData.reversed().forEach { data ->
                        Surface(
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable(
                                    onClick = { onClick(data.ticket.identifier) }
                                ),
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = 4.dp,
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
                                        darkMode = isDarkMode
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
        }
    }
}