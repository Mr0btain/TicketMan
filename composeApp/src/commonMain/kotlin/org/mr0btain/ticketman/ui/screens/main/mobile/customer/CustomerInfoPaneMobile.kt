package org.mr0btain.ticketman.ui.screens.main.mobile.customer

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
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.data.shared.formatTime
import org.mr0btain.ticketman.ui.screens.main.components.ContactsBlock
import org.mr0btain.ticketman.ui.screens.main.components.NotesBlock
import org.mr0btain.ticketman.ui.screens.main.components.SmallTicketList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit
import ticketman.composeapp.generated.resources.user


@Composable
fun CustomerInfoPaneMobile(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    scrollBarsOnRight: Boolean,
    selectedCustomer: Customer,
    tickets: List<Ticket>,
    onEditClick: () -> Unit,
    onTicketClick: (String) -> Unit
) {
    /*
    // Sizes for icons and text
    val labelSize = 12.sp
    val textSize = 16.sp
    val iconSize = 18.dp

    // Time formated
    val time = formatTime(selectedCustomer.time)

    // Main Body
    Column(
        modifier = modifier
            .padding(2.dp)
            .height(450.dp)
    ) {
        Column(
            modifier = Modifier
                .height(200.dp)
        ) {
            // --------------------------------- Header -----------------------------------
            Surface(
                modifier = Modifier.padding(4.dp),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(30.dp),
                        painter = painterResource(Res.drawable.user),
                        contentDescription = "User Information"
                    )

                    Spacer(Modifier.padding(4.dp))

                    Text(
                        modifier = Modifier
                            .padding(4.dp),
                        text = selectedCustomer.name.firstName + " " + selectedCustomer.name.lastName,
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
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(24.dp),
                                painter = painterResource(Res.drawable.edit),
                                contentDescription = "Edit customer details"
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp))

            // --------------------------------- First Pane: Basic details -----------------------------------
            Surface(
                modifier = Modifier
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 4.dp),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
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
                        if (selectedCustomer.time != Long.MAX_VALUE) {
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
                                text = selectedCustomer.address.street,
                                fontSize = textSize
                            )

                            // City and state
                            Text(
                                modifier = Modifier,
                                text = selectedCustomer.address.city + " " + selectedCustomer.address.state,
                                fontSize = textSize
                            )

                            // Zip
                            Text(
                                modifier = Modifier,
                                text = selectedCustomer.address.zip,
                                fontSize = textSize
                            )
                        }
                    }

                    // ------------ Contacts section ---------------
                    ContactsBlock(
                        customer = selectedCustomer,
                        labelSize = labelSize,
                        textSize = textSize,
                        iconSize = iconSize
                    )

                    // ------------ Notes section ---------------
                    NotesBlock(
                        notes = selectedCustomer.notes,
                        labelSize = labelSize,
                        textSize = textSize
                    )
                }
            }
        }

        // --------------------------------- Second Pane: Ticket List -----------------------------------
        SmallTicketList(
            modifier = Modifier,
            tickets = tickets,
            isDarkMode = isDarkMode,
            onClick = { ticketId -> onTicketClick(ticketId) },
            scrollBarsOnRight = scrollBarsOnRight
        )
    }
     */
}