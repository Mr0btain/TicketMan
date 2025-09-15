package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.data.ticket.TicketData
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.shared.PersonName
import org.mr0btain.ticketman.data.time.formatTime
import org.mr0btain.ticketman.ui.composables.ContactsBlock
import org.mr0btain.ticketman.ui.composables.SimpleTextItem
import org.mr0btain.ticketman.ui.composables.TablePriorityItem
import org.mr0btain.ticketman.ui.composables.TableStatusItem
import org.mr0btain.ticketman.ui.composables.note.NoteBlock
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit
import ticketman.composeapp.generated.resources.tag
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Composable
fun TicketInfoPane(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    customer: Customer,
    scrollBarsOnRight: Boolean,
    assignedUserName: PersonName,
    ticketData: TicketData?,
    onEditClick: () -> Unit,
    onImageClick: (List<ImageData>) -> Unit
) {
    val date = formatTime(ticketData?.ticket?.time ?: Long.MAX_VALUE)

    // Main Body
    Surface(
        modifier = modifier
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(2.dp)
        ) {
            // --------------------------------- Header -----------------------------------
            Surface(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .size(30.dp),
                        painter = painterResource(Res.drawable.tag),
                        contentDescription = "Ticket ID"
                    )

                    Spacer(Modifier.padding(4.dp))

                    ticketData?.let { (ticket, _) ->
                        Text(
                            modifier = Modifier
                                .padding(4.dp),
                            text = "Details for: ${ticket.identifier.toString().split("-").first()}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEditClick
                        ) {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                painter = painterResource(Res.drawable.edit),
                                contentDescription = "Edit Ticket details"
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row {
                // --------------------------------- First Pane -----------------------------------
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    // Basic details
                    Column(
                        modifier = Modifier
                            .width(300.dp)
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header
                        Text(
                            text = "Info",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        Spacer(Modifier.padding(4.dp))

                        ticketData?.let {
                            // Customer name
                            SimpleTextItem(
                                label = "Customer",
                                text = customer.name.firstName + " " + customer.name.lastName
                            )

                            Spacer(Modifier.padding(4.dp))

                            // Device manufacturer
                            SimpleTextItem(
                                label = "Manufacturer",
                                text = ticketData.ticket.device.manufacturer
                            )

                            Spacer(Modifier.padding(4.dp))

                            // Device model
                            SimpleTextItem(
                                label = "Model",
                                text = ticketData.ticket.device.model
                            )

                            Spacer(Modifier.padding(4.dp))

                            // Date created
                            SimpleTextItem(
                                label = "Created",
                                text = if (ticketData.ticket.time != Long.MAX_VALUE) "${date.month}/${date.day}/${date.year}" else ""
                            )

                            Spacer(Modifier.padding(4.dp))

                            // Assigned User
                            Column {
                                Text(
                                    text = "Assigned User:",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(Modifier.padding(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = assignedUserName.firstName + " " + assignedUserName.lastName,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            Spacer(Modifier.padding(4.dp))

                            // Contacts for customer block
                            ContactsBlock(
                                customer = customer,
                                labelSize = 16.sp
                            )
                        }
                    }
                }

                // --------------------------------- Second Pane -----------------------------------
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    ticketData?.let {
                        PlatformColumnScrollable(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(20.dp),
                            scrollBarOnRight = scrollBarsOnRight,
                            backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                            isVisible = ticketData.notes.isNotEmpty()
                        ) {
                            Text(
                                text = "Updates",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            HorizontalDivider()

                            Spacer(Modifier.padding(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Status:",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.padding(4.dp))

                                TableStatusItem(
                                    status = ticketData.ticket.status,
                                    darkMode = isDarkMode
                                )
                            }

                            Spacer(Modifier.padding(4.dp))

                            // Priority
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Priority:",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(Modifier.padding(4.dp))

                                TablePriorityItem(
                                    priority = ticketData.ticket.priority,
                                    darkMode = isDarkMode
                                )
                            }

                            Spacer(Modifier.padding(4.dp))

                            NoteBlock(
                                noteData = ticketData.notes,
                                onImageClick = onImageClick
                            )
                        }
                    }
                }
            }
        }
    }
}
