package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.ui.composables.*
import org.mr0btain.ticketman.ui.composables.note.NoteBlock
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
import ticketman.composeapp.generated.resources.*
import kotlin.uuid.Uuid

@Composable
fun TicketInfoPane(
    modifier: Modifier = Modifier,
    seed: TicketSeed,
    onEditClick: () -> Unit,
    onImageClick: (Uuid) -> Unit
) {
    // Main Body
    Surface(
        modifier = modifier
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp,
        color = if (CustomMaterialColors.darkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
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

                    if (!seed.isNew) {
                        Text(
                            modifier = Modifier
                                .padding(4.dp),
                            text = "Details for: ${seed.identifier.toString().split("-").first()}",
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
                            .padding(8.dp)
                    ) {
                        // Header
                        PaneHeader(
                            text = "Info",
                            icon = Res.drawable.info
                        )

                        Spacer(Modifier.padding(4.dp))

                        seed?.let {
                            PlatformLazyColumn(
                                modifier = Modifier.padding(4.dp),
                                isVisible = true
                            ) {
                                item {
                                    // Customer name
                                    SimpleTextItem(
                                        label = "Customer",
                                        text = seed.customer?.text ?: ""
                                    )

                                    Spacer(Modifier.padding(4.dp))

                                    // Device manufacturer
                                    SimpleTextItem(
                                        label = "Manufacturer",
                                        text = seed.ticket.device.manufacturer
                                    )

                                    Spacer(Modifier.padding(4.dp))

                                    // Device model
                                    SimpleTextItem(
                                        label = "Model",
                                        text = seed.ticket.device.model
                                    )

                                    Spacer(Modifier.padding(4.dp))

                                    // Date created
                                    SimpleTextItem(
                                        label = "Created",
                                        text = seed.time.toString()
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
                                                text = seed.user?.text ?: "",
                                                fontSize = 18.sp
                                            )
                                        }
                                    }

                                    Spacer(Modifier.padding(4.dp))

                                    // Contacts for customer block
                                    /*
                                    ContactsBlock(
                                        customer = customer,
                                        labelSize = 16.sp
                                    )
                                     */
                                }
                            }
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
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Header
                        PaneHeader(
                            text = "Updates",
                            icon = Res.drawable.clipboard
                        )

                        Spacer(Modifier.padding(8.dp))

                        seed.let {
                            PlatformLazyColumn(
                                modifier = Modifier.padding(8.dp),
                                isVisible = seed.notes.isNotEmpty()
                            ) {
                                item {
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

                                        TableStatusItem(status = seed.ticket.status)
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

                                        TablePriorityItem(priority = seed.ticket.priority)
                                    }

                                    Spacer(Modifier.padding(4.dp))

                                    NoteBlock(
                                        notes = seed.notes,
                                        onImageClick = onImageClick
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
