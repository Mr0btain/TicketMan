package org.mr0btain.ticketman.ui.screens.main.expanded.customer

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
import org.mr0btain.ticketman.components.data.customer.CustomerData
import org.mr0btain.ticketman.components.data.customer.CustomerSeed
import org.mr0btain.ticketman.ui.composables.ContactsBlock
import org.mr0btain.ticketman.ui.composables.PaneHeader
import org.mr0btain.ticketman.ui.composables.note.NoteBlock
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import org.mr0btain.ticketman.ui.composables.ticket.SmallTicketList
import ticketman.composeapp.generated.resources.*
import java.util.*

@Composable
fun CustomerInfoPane(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    selectedCustomer: CustomerSeed,
    onEditClick: () -> Unit,
    onTicketClick: (UUID) -> Unit,
    onImageClick: (UUID) -> Unit
) {
    // Sizes for icons and text
    val labelSize = 12.sp
    val textSize = 16.sp
    val iconSize = 18.dp

    // Main Body
    Surface(
        modifier = modifier
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 4.dp
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
                        painter = painterResource(Res.drawable.user),
                        contentDescription = "User Information"
                    )

                    Spacer(Modifier.padding(4.dp))

                    Text(
                        modifier = Modifier
                            .padding(4.dp),
                        text = selectedCustomer.name.toString(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )

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
                                contentDescription = "Edit customer details"
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row {
                // --------------------------------- First Pane: Basic details -----------------------------------
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .width(340.dp)
                        .fillMaxHeight(),
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
                            text = "Info",
                            icon = Res.drawable.info
                        )

                        PlatformLazyColumn(
                            modifier = Modifier.padding(4.dp),
                            isVisible = true
                        ) {
                            item {
                                // ----------- Date section ---------------
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    // Label
                                    Text(
                                        text = "Customer Since",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = labelSize
                                    )

                                    Spacer(Modifier.padding(4.dp))

                                    // Check if we have a sensible date to display, then show it

                                    Text(
                                        modifier = Modifier,
                                        text = selectedCustomer.time.date(),
                                        fontSize = textSize
                                    )
                                }

                                // ----------- Address ---------------
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Label
                                    Text(
                                        modifier = Modifier
                                            .padding(2.dp),
                                        text = "Address",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = labelSize
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.SpaceAround,
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        // Street
                                        Text(
                                            modifier = Modifier,
                                            text = selectedCustomer.street,
                                            fontSize = textSize
                                        )

                                        // City and state
                                        Text(
                                            modifier = Modifier,
                                            text = selectedCustomer.city + " " + selectedCustomer.state,
                                            fontSize = textSize
                                        )

                                        // Zip
                                        Text(
                                            modifier = Modifier,
                                            text = selectedCustomer.zip,
                                            fontSize = textSize
                                        )
                                    }
                                }

                                // ------------ Contacts section ---------------
                                ContactsBlock(
                                    emailList = selectedCustomer.email,
                                    phoneList = selectedCustomer.phone,
                                    labelSize = labelSize,
                                    textSize = textSize,
                                    iconSize = iconSize
                                )

                                // ------------ Notes section ---------------
                                NoteBlock(
                                    label = "Notes",
                                    notes = selectedCustomer.notes,
                                    onImageClick = onImageClick,
                                    textSize = textSize
                                )
                            }
                        }
                    }
                }

                // --------------------------------- Second Pane: Ticket List -----------------------------------

                Surface(
                    modifier = modifier
                        .fillMaxHeight()
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
                            text = "Tickets",
                            icon = Res.drawable.tag
                        )

                        SmallTicketList(
                            tickets = selectedCustomer.tickets,
                            isDarkMode = isDarkMode,
                            onClick = { ticketId -> onTicketClick(ticketId) }
                        )
                    }
                }
            }
        }
    }
}
