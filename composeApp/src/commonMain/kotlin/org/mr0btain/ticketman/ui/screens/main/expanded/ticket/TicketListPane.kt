package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumnScrollable
import org.mr0btain.ticketman.ui.screens.main.components.TableCardTextItem
import org.mr0btain.ticketman.ui.screens.main.components.TableDivider
import org.mr0btain.ticketman.ui.screens.main.components.TablePriorityItem
import org.mr0btain.ticketman.ui.screens.main.components.TableStatusItem
import org.mr0btain.ticketman.ui.screens.main.components.TableTicketIdItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: When we switch from all tickets to my tickets we need to not scroll to the item if it doesn't exist

@OptIn(ExperimentalUuidApi::class)
@Composable
fun TicketListPane(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    ticket: List<Ticket> = listOf(),
    scrollBarsOnRight: Boolean,
    selectedId: Uuid,
    onClick: (Uuid) -> Unit,
    state: LazyListState = rememberLazyListState(),
    coroutineScope: CoroutineScope
) {
    // Column widths
    val numberColumnWidth = 100.dp
    val priorityColumnWidth = 100.dp
    val noteColumnWidth = 320.dp
    val statusColumnWidth = 120.dp

    Surface(
        modifier = modifier
            .wrapContentSize()
            .padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        color = if (isDarkMode) MaterialTheme.colorScheme.surfaceBright else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(start = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.padding(3.dp))

                TicketHeaderBox(
                    label = "Ticket #",
                    width = numberColumnWidth
                )

                Spacer(Modifier.padding(10.dp))

                TicketHeaderBox(
                    label = "Priority",
                    width = priorityColumnWidth
                )

                Spacer(Modifier.padding(9.dp))

                TicketHeaderBox(
                    label = "Latest Update",
                    width = noteColumnWidth
                )

                Spacer(Modifier.padding(10.dp))

                TicketHeaderBox(
                    label = "Status",
                    width = statusColumnWidth,
                    dividerEnable = false
                )
            }

            HorizontalDivider(modifier = Modifier.width(710.dp))

            PlatformLazyColumnScrollable(
                modifier = Modifier
                    .width(740.dp)
                    .padding(start = 4.dp),
                state = state,
                scope = coroutineScope,
                scrollBarOnRight = scrollBarsOnRight,
                isVisible = ticket.isNotEmpty()
            ) {
                itemsIndexed(ticket) { index, item ->
                    TicketTableItem(
                        color = if (!isDarkMode) {
                            if (index % 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                        } else {
                            if (index % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        },
                        selected = selectedId == item.identifier,
                        ticket = item,
                        numberWidth = numberColumnWidth,
                        priorityWidth = priorityColumnWidth,
                        noteWidth = noteColumnWidth,
                        statusWidth = statusColumnWidth,
                        isDarkMode = isDarkMode,
                        onClick = { onClick(item.identifier) }
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

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun TicketTableItem(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    selected: Boolean,
    ticket: Ticket,
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
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(numberWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableTicketIdItem(
                    label = ticket.identifier.toString(),
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                )
            }

            TableDivider()

            Box(
                modifier = Modifier
                    .padding(start = 5.dp)
                    .width(priorityWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {

                TablePriorityItem(
                    priority = ticket.priority,
                    darkMode = isDarkMode,
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                )
            }

            TableDivider()

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(noteWidth + sizeAdjust.dp)
            ) {
                /*
                if (ticket.updates.isNotEmpty()) {
                    TableCardTextItem(
                        ticket.updates.last().note.author + " : " + ticket.updates.last().note.text
                    )
                }
                 */
            }

            TableDivider()

            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(statusWidth + sizeAdjust.dp),
                contentAlignment = Alignment.Center
            ) {
                TableStatusItem(
                    status = ticket.status,
                    darkMode = isDarkMode,
                    textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
    }
}