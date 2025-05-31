package org.mr0btain.ticketman.ui.screens.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.components.data.components.TicketInfo
import org.mr0btain.ticketman.components.data.components.TicketPriority
import org.mr0btain.ticketman.components.data.components.TicketStatus
import org.mr0btain.ticketman.ui.composables.scrollbar.LazyVerticalScrollBar
import org.mr0btain.ticketman.ui.theme.extendedDark
import org.mr0btain.ticketman.ui.theme.extendedLight

@Composable
fun TicketListPane(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    ticketInfo: List<TicketInfo> = listOf(),
    selectedId: String,
    scrollOnRight: Boolean,
    onClick: (TicketInfo) -> Unit,
    state: LazyListState = rememberLazyListState()
) {

// Ticket #  |  Priority  |   Last Note   |   Status

    // Column widths
    val numberColumnWidth = 100.dp
    val priorityColumnWidth = 100.dp
    val noteColumnWidth = 360.dp
    val statusColumnWidth = 120.dp

    Surface(
        modifier = modifier
            .wrapContentSize()
            .padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 10.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
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

                TicketHeaderBox(
                    label = "Ticket #",
                    width = numberColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                TicketHeaderBox(
                    label = "Priority",
                    width = priorityColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                TicketHeaderBox(
                    label = "Latest Note",
                    width = noteColumnWidth
                )

                Spacer(Modifier.padding(10.dp))

                TicketHeaderBox(
                    label = "Status",
                    width = statusColumnWidth,
                    dividerEnable = false
                )
            }

            HorizontalDivider(modifier = Modifier.width(750.dp))

            LazyVerticalScrollBar(
                state = state
            ) {
                itemsIndexed(ticketInfo) { index, item ->
                    TicketTableItem(
                        color = if (!isDarkMode) {
                            if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        } else {
                            if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        },
                        selected = selectedId == item.idNum,
                        ticketInfo = item,
                        numberWidth = numberColumnWidth,
                        priorityWidth = priorityColumnWidth,
                        noteWidth = noteColumnWidth,
                        statusWidth = statusColumnWidth,
                        isDarkMode = isDarkMode,
                        onClick = { onClick(item) }
                    )

                    Spacer(Modifier.padding(2.dp))
                }
            }
        }
    }
}

@Composable
private fun TicketHeaderBox(
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
private fun TicketTableItem(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    selected: Boolean,
    ticketInfo: TicketInfo,
    numberWidth: Dp,
    priorityWidth: Dp,
    noteWidth: Dp,
    statusWidth: Dp,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .padding(2.dp)
            .clickable(
                onClick = { onClick() }
            ),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        color = color,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent)
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
                    label = ticketInfo.idNum,
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
                        TicketPriority.HIGH -> if (isDarkMode) extendedDark.inProgress.color else extendedLight.inProgress.color
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
                    ticketInfo.updates.last().author + " : " + ticketInfo.updates.last().text
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
                        TicketStatus.IN_PROGRESS -> if (isDarkMode) extendedDark.inProgress.color else extendedLight.inProgress.color
                        TicketStatus.ON_HOLD -> MaterialTheme.colorScheme.error
                        TicketStatus.DONE -> if (isDarkMode) extendedDark.onSuccess.color else extendedLight.onSuccess.color
                    }
                )
            }
        }
    }
}