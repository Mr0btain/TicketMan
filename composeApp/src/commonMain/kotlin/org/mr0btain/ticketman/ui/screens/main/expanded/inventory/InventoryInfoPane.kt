package org.mr0btain.ticketman.ui.screens.main.expanded.inventory

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
import org.mr0btain.ticketman.components.data.inventory.InventorySeed
import org.mr0btain.ticketman.data.uuid.UUIDv7
import org.mr0btain.ticketman.ui.composables.PaneHeader
import org.mr0btain.ticketman.ui.composables.SimpleTextItem
import org.mr0btain.ticketman.ui.composables.note.NoteBlock
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import ticketman.composeapp.generated.resources.*

@Composable
fun InventoryInfoPane(
    modifier: Modifier = Modifier,
    isDarkMode: Boolean,
    inventoryData: InventorySeed,
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
                        painter = painterResource(Res.drawable.package_icon),
                        contentDescription = "Ticket ID"
                    )

                    Spacer(Modifier.padding(4.dp))

                    Text(
                        modifier = Modifier
                            .padding(4.dp),
                        text = inventoryData?.inventory?.name ?: "",
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

            // TODO: Add photo options
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
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Header
                        PaneHeader(
                            text = "Details",
                            icon = Res.drawable.file_text,
                            contentDescription = "Details"
                        )

                        Spacer(Modifier.padding(4.dp))

                        if (!inventoryData.isNew) {
                            // Description
                            Column(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "Description:",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(Modifier.padding(4.dp))

                                Text(
                                    text = inventoryData.description,
                                    fontSize = 16.sp
                                )
                            }

                            Spacer(Modifier.padding(4.dp))

                            // Cost
                            SimpleTextItem(
                                label = "Cost",
                                text = inventoryData.cost.toString()
                            )

                            Spacer(Modifier.padding(4.dp))

                            // Price
                            SimpleTextItem(
                                label = "Price",
                                text = inventoryData.price.toString()
                            )

                            Spacer(Modifier.padding(4.dp))

                            // Date created
                            SimpleTextItem(
                                label = "Created",
                                text = UUIDv7.getTime(inventoryData.identifier).date()
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
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Header
                        PaneHeader(
                            text = "Notes",
                            icon = Res.drawable.clipboard
                        )

                        if (!inventoryData.isNew) {
                            PlatformLazyColumn(
                                modifier = Modifier.padding(4.dp),
                                isVisible = inventoryData.notes.isNotEmpty()
                            ) {
                                item {
                                    NoteBlock(notes = inventoryData.notes)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}