package org.mr0btain.ticketman.ui.screens.main.components

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
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.helpers.formatTime
import ticketman.composeapp.generated.resources.*

@Composable
fun CustomerInfoPane(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    selectedCustomerData: CustomerData,
    tickets: List<TicketInfo>,
    onEditClick: () -> Unit,
    onTicketClick: (Int) -> Unit
) {
    // Scroll state for basic ifo pane if needed
    val scrollState = rememberScrollState()

    // Sizes for icons and text
    val labelSize = 12.sp
    val textSize = 16.sp
    val iconSize = 18.dp

    // Time formated
    val time = formatTime(selectedCustomerData.info.time)

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
                        painter = painterResource(Res.drawable.user),
                        contentDescription = "User Information"
                    )

                    Spacer(Modifier.padding(4.dp))

                    Text(
                        modifier = Modifier
                            .padding(4.dp),
                        text = selectedCustomerData.info.name.firstName + " " + selectedCustomerData.info.name.lastName,
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
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier
                            .width(300.dp)
                            .padding(20.dp)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Text(
                            text = "Info",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

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
                            if (selectedCustomerData.info.time != Long.MAX_VALUE) {
                                Text(
                                    modifier = Modifier,
                                    text = "${time.month}/${time.day}/${time.year}",
                                    fontSize = textSize
                                )
                            }
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
                                    text = selectedCustomerData.info.address.street,
                                    fontSize = textSize
                                )

                                // City and state
                                Text(
                                    modifier = Modifier,
                                    text = selectedCustomerData.info.address.city + " " + selectedCustomerData.info.address.state,
                                    fontSize = textSize
                                )

                                // Zip
                                Text(
                                    modifier = Modifier,
                                    text = selectedCustomerData.info.address.zip,
                                    fontSize = textSize
                                )
                            }
                        }

                        // ------------ Contacts section ---------------
                        ContactsBlock(
                            customerInfo = selectedCustomerData.info,
                            labelSize = labelSize,
                            textSize = textSize,
                            iconSize = iconSize
                        )

                        // ------------ Notes section ---------------
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Label
                            Text(
                                modifier = Modifier
                                    .padding(2.dp),
                                text = "Notes",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = labelSize
                            )

                            Spacer(Modifier.padding(2.dp))

                            selectedCustomerData.notes.forEach { note ->
                                // TODO: Add date and time to this
                                Column {
                                    // Author
                                    Text(
                                        modifier = Modifier,
                                        text = note.author + ": ",
                                        fontSize = textSize,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    // Text
                                    Text(
                                        modifier = Modifier,
                                        text = note.text,
                                        fontSize = textSize
                                    )
                                }
                            }
                        }
                    }
                }

                // --------------------------------- Second Pane: Ticket List -----------------------------------
                SmallTicketList(
                    modifier = Modifier.fillMaxHeight(),
                    tickets = tickets,
                    isDarkMode = isDarkMode,
                    onClick = { ticketId -> onTicketClick(ticketId) }
                )
            }
        }
    }
}
