package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.data.*
import org.mr0btain.ticketman.ui.composables.scrollbar.VerticalScrollBar
import org.mr0btain.ticketman.ui.screens.main.components.TableCardTextItem
import org.mr0btain.ticketman.ui.screens.main.components.TableStatusItem

@Composable
fun TicketDataGrid(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    ticketInfo: List<TicketInfo> = listOf()
) {

// Ticket #  |  Priority  |   Last Note   |   Status

    // Column widths
    val numberColumnWidth = 100.dp
    val priorityColumnWidth = 140.dp
    val noteColumnWidth = 360.dp
    val statusColumnWidth = 180.dp

    // Scroll stuff
    val horizontalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .wrapContentSize()
            .padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 10.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.padding(10.dp))

                HeaderBox(
                    label = "Ticket #",
                    width = numberColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                HeaderBox(
                    label = "Priority",
                    width = priorityColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                HeaderBox(
                    label = "Latest Note",
                    width = noteColumnWidth
                )

                Spacer(Modifier.padding(10.dp))

                HeaderBox(
                    label = "Status",
                    width = statusColumnWidth,
                    dividerEnable = false
                )
            }

            HorizontalDivider(modifier = Modifier.width(850.dp))

            VerticalScrollBar(
                modifier = Modifier.wrapContentSize(),
                scope = scope,
                clipToContent = true
            ) {
                Column(
                    modifier = Modifier.horizontalScroll(horizontalScrollState)
                ) {
                    // Almost have to use manual indexing to alternate item colors. If you use forEachIndexed or something of the like
                    // it ends up producing wrong values for some reason
                    val indexed = remember { mutableIntStateOf(0) }

                    repeat(20) {
                        TableItem(
                            color = if (indexed.value % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            ticketInfo = TicketInfo(
                                "1234",
                                Device("Apple", "IPhone16"),
                                randomTicketStatus(),
                                randomTicketPriority(),
                                notes = listOf(
                                    Note(
                                        "Mr. Author",
                                        "Still working on the device"
                                    )
                                )),
                            numberWidth = numberColumnWidth,
                            priorityWidth = priorityColumnWidth,
                            noteWidth = noteColumnWidth,
                            statusWidth = statusColumnWidth
                        )

                        Spacer(Modifier.padding(2.dp))

                        indexed.value++
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBox(
    modifier: Modifier = Modifier,
    label: String,
    width: Dp,
    dividerEnable: Boolean = true
) {
    Row(
        modifier.width(width),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )

        if (dividerEnable) {
            VerticalDivider(Modifier.height(42.dp), thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// TODO: Make onclick go to customer details screen when its done. or a popup?

@Composable
private fun TableItem(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    ticketInfo: TicketInfo,
    numberWidth: Dp,
    priorityWidth: Dp,
    noteWidth: Dp,
    statusWidth: Dp
) {
    val yellowColor = Color(red = 230, green = 218, blue = 2)
    val greenColor = Color(red = 6, green = 138, blue = 52)

    Surface(
        modifier = modifier
            .padding(2.dp)
            .clickable(
                onClick = {
                    // Do something
                }
            ),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        color = color
    ) {
        val sizeAdjust = -8

        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TODO: Setup an actual composable for this
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(numberWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableStatusItem(
                    label = ticketInfo.id,
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp),
                    backgroundColor = MaterialTheme.colorScheme.primary
                )
                //TableCardTextItem(ticketInfo.id)
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 5.dp)
                    .width(priorityWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableStatusItem(
                    label = ticketInfo.priority.name,
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary),
                    backgroundColor = when (ticketInfo.priority) {
                        TicketPriority.LOW -> MaterialTheme.colorScheme.secondary
                        TicketPriority.NORMAL -> MaterialTheme.colorScheme.primary
                        TicketPriority.HIGH -> yellowColor
                        TicketPriority.URGENT -> MaterialTheme.colorScheme.error
                    }
                )
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            // TODO: Setup an actual composable for this

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(noteWidth + sizeAdjust.dp)
            ) {
                TableCardTextItem(
                    ticketInfo.notes.last().author + " : " + ticketInfo.notes.last().text
                )
            }

            Row(
                modifier = Modifier.width(22.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    thickness = 2.dp
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(statusWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableStatusItem(
                    label = ticketInfo.status.name,
                    textStyle = if (ticketInfo.status.name == "ON_HOLD") {
                        TextStyle.Default.copy(fontSize = 12.sp)
                    } else TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary),
                    backgroundColor = when (ticketInfo.status) {
                        TicketStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
                        TicketStatus.ON_HOLD -> MaterialTheme.colorScheme.error
                        TicketStatus.DONE -> greenColor
                    }
                )

                // Yellow Color(0xF5E911)
            }
        }
    }
}