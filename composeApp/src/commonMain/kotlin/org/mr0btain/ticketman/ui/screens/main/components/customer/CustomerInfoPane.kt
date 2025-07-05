package org.mr0btain.ticketman.ui.screens.main.components.customer

import androidx.compose.foundation.background
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
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.shared.formatTime
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import org.mr0btain.ticketman.ui.screens.main.components.ContactsBlock
import org.mr0btain.ticketman.ui.screens.main.components.NotesBlock
import org.mr0btain.ticketman.ui.screens.main.components.SmallTicketList
import ticketman.composeapp.generated.resources.*

@Composable
fun CustomerInfoPane(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    scrollBarsOnRight: Boolean,
    selectedCustomerData: CustomerData,
    tickets: List<TicketInfo>,
    onEditClick: () -> Unit,
    onTicketClick: (String) -> Unit
) {
    // Sizes for icons and text
    val labelSize = 12.sp
    val textSize = 16.sp
    val iconSize = 18.dp

    // Time formated
    val time = formatTime(selectedCustomerData.time)

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
                        text = selectedCustomerData.name.firstName + " " + selectedCustomerData.name.lastName,
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
                    PlatformColumnScrollable(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .width(300.dp)
                            .padding(20.dp),
                        scrollBarOnRight = scrollBarsOnRight,
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                        isVisible = true
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
                            if (selectedCustomerData.time != Long.MAX_VALUE) {
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
                                    text = selectedCustomerData.address.street,
                                    fontSize = textSize
                                )

                                // City and state
                                Text(
                                    modifier = Modifier,
                                    text = selectedCustomerData.address.city + " " + selectedCustomerData.address.state,
                                    fontSize = textSize
                                )

                                // Zip
                                Text(
                                    modifier = Modifier,
                                    text = selectedCustomerData.address.zip,
                                    fontSize = textSize
                                )
                            }
                        }

                        // ------------ Contacts section ---------------
                        ContactsBlock(
                            customerData = selectedCustomerData,
                            labelSize = labelSize,
                            textSize = textSize,
                            iconSize = iconSize
                        )

                        // ------------ Notes section ---------------
                        NotesBlock(
                            notes = selectedCustomerData.notes,
                            labelSize = labelSize,
                            textSize = textSize
                        )
                    }
                }

                // --------------------------------- Second Pane: Ticket List -----------------------------------
                SmallTicketList(
                    modifier = Modifier.fillMaxHeight(),
                    tickets = tickets,
                    isDarkMode = isDarkMode,
                    onClick = { ticketId -> onTicketClick(ticketId) },
                    scrollBarsOnRight = scrollBarsOnRight
                )
            }
        }
    }
}
