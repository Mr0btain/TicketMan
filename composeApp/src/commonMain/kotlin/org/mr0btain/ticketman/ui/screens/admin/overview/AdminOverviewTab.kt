package org.mr0btain.ticketman.ui.screens.admin.overview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.ui.composables.TablePriorityItem
import org.mr0btain.ticketman.ui.composables.TableStatusItem
import org.mr0btain.ticketman.ui.screens.admin.components.*
import org.mr0btain.ticketman.ui.theme.CustomMaterialColors
import ticketman.composeapp.generated.resources.*
import kotlin.uuid.Uuid

/**
 * Overview numbers for the shop
 * @param onTicketClick Opens a ticket on the main screen
 * @param onLowStockClick Jumps to the low stock tab
 */
@Composable
fun AdminOverviewTab(
    stats: AdminStatsResponse?,
    onTicketClick: (Uuid) -> Unit,
    onLowStockClick: () -> Unit
) {
    if (stats == null) {
        EmptyMessage("No stats loaded yet. Make sure you're connected and logged in as an admin, then hit Refresh.")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // ------------------------------------------ Headline numbers ------------------------------------------
        FlowRow {
            StatCard(
                label = "Open Tickets",
                value = stats.openTickets.toString(),
                icon = Res.drawable.tag,
                caption = "${stats.totalTickets} total"
            )

            StatCard(
                label = "New This Week",
                value = stats.ticketsCreatedLastWeek.toString(),
                icon = Res.drawable.trending_up,
                caption = "Tickets in the last 7 days"
            )

            StatCard(
                label = "Customers",
                value = stats.totalCustomers.toString(),
                icon = Res.drawable.users
            )

            StatCard(
                label = "Low Stock",
                value = stats.lowStockCount.toString(),
                icon = Res.drawable.alert_triangle,
                caption = "At or below ${stats.lowStockThreshold} of ${stats.totalInventory} items",
                highlight = stats.lowStockCount > 0
            )

            StatCard(
                label = "Users",
                value = stats.totalUsers.toString(),
                icon = Res.drawable.user,
                caption = "${stats.activeSessions} logged in"
            )
        }

        // ------------------------------------------ Breakdowns ------------------------------------------
        FlowRow {
            SectionCard(
                modifier = Modifier.width(360.dp),
                title = "Tickets by Status"
            ) {
                TicketStatusType.entries.forEach { status ->
                    CountBar(
                        label = formatEnumName(status.name),
                        count = stats.ticketsByStatus[status] ?: 0,
                        total = stats.totalTickets,
                        color = when (status) {
                            TicketStatusType.IN_PROGRESS -> CustomMaterialColors.inProgress
                            TicketStatusType.ON_HOLD -> MaterialTheme.colorScheme.error
                            TicketStatusType.DONE -> CustomMaterialColors.success
                            TicketStatusType.PICKED_UP -> MaterialTheme.colorScheme.secondary
                        }
                    )
                }
            }

            SectionCard(
                modifier = Modifier.width(360.dp),
                title = "Open Tickets by Priority"
            ) {
                TicketPriorityType.entries.forEach { priority ->
                    CountBar(
                        label = formatEnumName(priority.name),
                        count = stats.ticketsByPriority[priority] ?: 0,
                        total = stats.openTickets,
                        color = when (priority) {
                            TicketPriorityType.LOW -> MaterialTheme.colorScheme.secondary
                            TicketPriorityType.NORMAL -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            SectionCard(
                modifier = Modifier.width(360.dp),
                title = "Users by Access"
            ) {
                listOf(AccessLevel.RESTRICTED, AccessLevel.NORMAL, AccessLevel.ADMIN).forEach { level ->
                    CountBar(
                        label = level.label,
                        count = stats.usersByAccessLevel[level] ?: 0,
                        total = stats.totalUsers
                    )
                }

                CountBar(
                    label = "Pending",
                    count = stats.pendingUsers,
                    total = stats.totalUsers,
                    color = CustomMaterialColors.inProgress
                )

                CountBar(
                    label = "Deactivated",
                    count = stats.deactivatedUsers,
                    total = stats.totalUsers,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // ------------------------------------------ Tickets ------------------------------------------
        FlowRow {
            SectionCard(
                modifier = Modifier.width(360.dp),
                title = "Oldest Open Ticket"
            ) {
                val oldest = stats.oldestOpenTicket

                if (oldest == null) {
                    EmptyMessage("No open tickets")
                } else {
                    TicketRow(oldest, onClick = { onTicketClick(oldest.identifier) })

                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = "Open for ${formatTimeAgo(oldest.identifier.timestampMillis()).removeSuffix(" ago")}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (stats.lowStockCount > 0) {
                    Spacer(Modifier.padding(8.dp))

                    TextButton(onClick = onLowStockClick) {
                        Text("View ${stats.lowStockCount} low stock items")
                    }
                }
            }

            SectionCard(
                modifier = Modifier.width(560.dp),
                title = "Recent Tickets"
            ) {
                if (stats.recentTickets.isEmpty()) {
                    EmptyMessage("No tickets yet")
                } else {
                    stats.recentTickets.forEach { ticket ->
                        TicketRow(ticket, onClick = { onTicketClick(ticket.identifier) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketRow(
    ticket: Ticket,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = ticket.device.toString().ifBlank { "Unknown device" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = formatTimeAgo(ticket.identifier.timestampMillis()),
                fontSize = 12.sp
            )

            TableStatusItem(
                status = ticket.status,
                textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
            )

            TablePriorityItem(
                priority = ticket.priority,
                textStyle = TextStyle.Default.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
            )
        }
    }
}
