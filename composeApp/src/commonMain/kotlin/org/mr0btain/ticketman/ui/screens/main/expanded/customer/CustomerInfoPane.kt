package org.mr0btain.ticketman.ui.screens.main.expanded.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.customer.CustomerData
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.data.time.formatTime
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import org.mr0btain.ticketman.ui.screens.main.components.ContactsBlock
import org.mr0btain.ticketman.ui.screens.main.components.NotesBlock
import org.mr0btain.ticketman.ui.screens.main.components.SmallTicketList
import ticketman.composeapp.generated.resources.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun CustomerInfoPane(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    scrollBarsOnRight: Boolean,
    selectedCustomer: CustomerData?,
    onEditClick: () -> Unit,
    onTicketClick: (Uuid) -> Unit,
    onImageClick: (List<ImageData>) -> Unit
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
                        text = selectedCustomer?.customer?.let { customer ->
                            customer.name.firstName + " " + customer.name.lastName
                        } ?: "",
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
                        // Main Title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                modifier = Modifier.size(20.dp),
                                painter = painterResource(Res.drawable.info),
                                contentDescription = "Info"
                            )

                            Spacer(Modifier.padding(4.dp))

                            Text(
                                text = "Info",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        HorizontalDivider()

                        selectedCustomer?.let { (customer, notes, _) ->
                            PlatformColumnScrollable(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(20.dp),
                                scrollBarOnRight = scrollBarsOnRight,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                isVisible = true
                            ) {
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
                                    if (customer.time != Long.MAX_VALUE) {
                                        // Time formated
                                        val time = formatTime(customer.time)

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
                                            text = customer.address.street,
                                            fontSize = textSize
                                        )

                                        // City and state
                                        Text(
                                            modifier = Modifier,
                                            text = customer.address.city + " " + customer.address.state,
                                            fontSize = textSize
                                        )

                                        // Zip
                                        Text(
                                            modifier = Modifier,
                                            text = customer.address.zip,
                                            fontSize = textSize
                                        )
                                    }
                                }

                                // ------------ Contacts section ---------------
                                ContactsBlock(
                                    customer = customer,
                                    labelSize = labelSize,
                                    textSize = textSize,
                                    iconSize = iconSize
                                )

                                // ------------ Notes section ---------------
                                NotesBlock(
                                    label = "Notes",
                                    noteData = notes,
                                    onImageClick = onImageClick,
                                    textSize = textSize,
                                    maxImageSize = DpSize(150.dp, 75.dp)
                                )
                            }
                        }
                    }
                }

                // --------------------------------- Second Pane: Ticket List -----------------------------------
                SmallTicketList(
                    modifier = Modifier.fillMaxHeight(),
                    ticketData = selectedCustomer?.tickets,
                    isDarkMode = isDarkMode,
                    onClick = { ticketId -> onTicketClick(ticketId) },
                    scrollBarsOnRight = scrollBarsOnRight
                )
            }
        }
    }
}
