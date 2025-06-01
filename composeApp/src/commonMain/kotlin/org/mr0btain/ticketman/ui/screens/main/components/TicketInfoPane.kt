package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.CustomerData
import org.mr0btain.ticketman.components.data.components.Employee
import org.mr0btain.ticketman.components.data.components.Note
import org.mr0btain.ticketman.components.data.components.PersonName
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.data.components.TicketPriority
import org.mr0btain.ticketman.components.data.components.TicketStatus
import org.mr0btain.ticketman.ui.theme.extendedDark
import org.mr0btain.ticketman.ui.theme.extendedLight
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit
import ticketman.composeapp.generated.resources.tag

@Composable
fun TicketInfoPane(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    customerData: CustomerData,
    employeeName: PersonName,
    ticketInfo: TicketInfo,
    onEditClick: () -> Unit
) {
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
            // Top bar
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

                    Text(
                        modifier = Modifier
                            .padding(4.dp),
                        text = "Details for: ${ticketInfo.identifier}",
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
                                contentDescription = "Edit Ticket details"
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row {
                // First Pane
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
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Info",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        Spacer(Modifier.padding(4.dp))

                        // Customer name
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Customer:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.padding(4.dp))

                            Text(
                                text = customerData.info.name.firstName + " " + customerData.info.name.lastName,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(Modifier.padding(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Manufacturer:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.padding(4.dp))

                            Text(
                                text = ticketInfo.device.manufacturer,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(Modifier.padding(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Model:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.padding(4.dp))

                            Text(
                                text = ticketInfo.device.model,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(Modifier.padding(4.dp))

                        // Assigned employee
                        Column {
                            Text(
                                text = "Assigned Employee:",
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
                                    text = ticketInfo.assignedEmployee + ": " + employeeName.firstName + " " + employeeName.lastName,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(Modifier.padding(4.dp))

                        // Contacts for customer block
                        ContactBlock(
                            data = customerData.info,
                            labelFont = 16.sp
                        )
                    }
                }

                // Second Pane
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
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

                            if (ticketInfo.identifier.isNotBlank()) {
                                TableStatusItem(
                                    status = ticketInfo.status,
                                    darkMode = isDarkMode
                                )
                            }
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

                            if (ticketInfo.identifier.isNotBlank()) {
                                TablePriorityItem(
                                    priority = ticketInfo.priority,
                                    darkMode = isDarkMode
                                )
                            }
                        }

                        Spacer(Modifier.padding(4.dp))

                        UpdatesBlock(data = ticketInfo.updates)
                    }
                }
            }
        }
    }
}

// Notes list
@Composable
private fun UpdatesBlock(data: List<Note>) {
    val textFont = 16.sp

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        data.forEach { note ->
            Column {
                // Author
                Text(
                    modifier = Modifier,
                    text = note.author + ": ",
                    fontSize = textFont,
                    fontWeight = FontWeight.SemiBold
                )

                // Text
                Text(
                    modifier = Modifier,
                    text = note.text,
                    fontSize = textFont
                )
            }
        }
    }
}