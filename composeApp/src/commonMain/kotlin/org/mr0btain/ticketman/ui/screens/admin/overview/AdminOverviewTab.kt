package org.mr0btain.ticketman.ui.screens.admin.overview

import org.mr0btain.ticketman.ui.kit.data.formatMoney
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.components.utils.formatTimeAgo
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.time.timestampMillis
import org.mr0btain.ticketman.server.response.AdminStatsResponse
import org.mr0btain.ticketman.server.response.DailyReport
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmCountBar
import org.mr0btain.ticketman.ui.kit.data.TmStatTile
import org.mr0btain.ticketman.ui.kit.feedback.TmPriorityBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmStatusBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.label
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.components.label
import org.mr0btain.ticketman.ui.screens.counter.PrintButton
import ticketman.composeapp.generated.resources.*
import kotlin.uuid.Uuid

/** Width of a stat tile, they wrap onto new rows */
private val TILE_WIDTH = 180.dp

/** Width of the breakdown panels */
private val PANEL_WIDTH = 360.dp

/**
 * Overview numbers for the shop
 * @param onTicketClick Opens a ticket on the main screen
 * @param onLowStockClick Jumps to the low stock tab
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminOverviewTab(
    stats: AdminStatsResponse?,
    dailyReport: DailyReport?,
    defaultPaper: PaperSize,
    onMoveReport: (Int) -> Unit,
    onPrintReport: (PaperSize, (String?) -> Unit) -> Unit,
    onTicketClick: (Uuid) -> Unit,
    onLowStockClick: () -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    if (stats == null) {
        TmEmptyState(
            icon = Res.drawable.bar_chart_2,
            title = "No numbers yet",
            message = "Make sure you're connected and logged in as an admin, then refresh."
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        // ------------------------------------------ Headline numbers ------------------------------------------
        TileRow {
            TmStatTile(modifier = Modifier.width(TILE_WIDTH), label = "Open tickets", value = stats.openTickets.toString(), icon = Res.drawable.tag, caption = "${stats.totalTickets} total")
            TmStatTile(modifier = Modifier.width(TILE_WIDTH), label = "New this week", value = stats.ticketsCreatedLastWeek.toString(), icon = Res.drawable.trending_up, caption = "Tickets in the last 7 days")
            TmStatTile(modifier = Modifier.width(TILE_WIDTH), label = "Customers", value = stats.totalCustomers.toString(), icon = Res.drawable.users)
            TmStatTile(
                modifier = Modifier.width(TILE_WIDTH),
                label = "Low stock",
                value = stats.lowStockCount.toString(),
                icon = Res.drawable.alert_triangle,
                caption = "At or below ${stats.lowStockThreshold} of ${stats.totalInventory} items",
                attention = stats.lowStockCount > 0,
                onClick = onLowStockClick
            )
            TmStatTile(modifier = Modifier.width(TILE_WIDTH), label = "Users", value = stats.totalUsers.toString(), icon = Res.drawable.user, caption = "${stats.activeSessions} logged in")
        }

        // ------------------------------------------ End of day ------------------------------------------
        dailyReport?.let { report -> DailyReportCard(report, defaultPaper, onMoveReport, onPrintReport) }

        // ------------------------------------------ Breakdowns ------------------------------------------
        TileRow {
            TmPanel(modifier = Modifier.width(PANEL_WIDTH), title = "Tickets by status", icon = Res.drawable.pie_chart) {
                Column {
                    TicketStatusType.entries.forEach { status ->
                        TmCountBar(
                            label = status.label,
                            count = stats.ticketsByStatus[status] ?: 0,
                            total = stats.totalTickets,
                            color = colors.tone(status).color
                        )
                    }
                }
            }

            TmPanel(modifier = Modifier.width(PANEL_WIDTH), title = "Open tickets by priority", icon = Res.drawable.flag) {
                Column {
                    TicketPriorityType.entries.forEach { priority ->
                        TmCountBar(
                            label = formatEnumName(priority.name),
                            count = stats.ticketsByPriority[priority] ?: 0,
                            total = stats.openTickets,
                            color = colors.tone(priority).color
                        )
                    }
                }
            }

            TmPanel(modifier = Modifier.width(PANEL_WIDTH), title = "Users by access", icon = Res.drawable.shield) {
                Column {
                    listOf(AccessLevel.RESTRICTED, AccessLevel.NORMAL, AccessLevel.ADMIN).forEach { level ->
                        TmCountBar(label = level.label, count = stats.usersByAccessLevel[level] ?: 0, total = stats.totalUsers, color = colors.tone(level).color)
                    }
                    TmCountBar(label = "Pending", count = stats.pendingUsers, total = stats.totalUsers, color = colors.warning.color)
                    TmCountBar(label = "Deactivated", count = stats.deactivatedUsers, total = stats.totalUsers, color = colors.danger.color)
                }
            }
        }

        // ------------------------------------------ Tickets ------------------------------------------
        TileRow {
            TmPanel(modifier = Modifier.width(PANEL_WIDTH), title = "Oldest open ticket", icon = Res.drawable.clock) {
                val oldest = stats.oldestOpenTicket

                if (oldest == null) {
                    Text(text = "No open tickets", style = TmTheme.typography.body, color = colors.textMuted)
                } else {
                    OverviewTicketRow(oldest, onClick = { onTicketClick(oldest.identifier) })

                    Text(
                        text = "Open for ${formatTimeAgo(oldest.identifier.timestampMillis()).removeSuffix(" ago")}",
                        style = TmTheme.typography.body,
                        color = colors.danger.color
                    )
                }

                if (stats.lowStockCount > 0) {
                    TmButton(text = "Show ${stats.lowStockCount} low stock items", onClick = onLowStockClick, variant = TmButtonVariant.SECONDARY)
                }
            }

            TmPanel(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(), title = "Recent tickets", icon = Res.drawable.tool) {
                if (stats.recentTickets.isEmpty()) {
                    Text(text = "No tickets yet", style = TmTheme.typography.body, color = colors.textMuted)
                }

                Column {
                    stats.recentTickets.forEach { ticket ->
                        OverviewTicketRow(ticket, onClick = { onTicketClick(ticket.identifier) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)
    ) { content() }
}

@Composable
private fun OverviewTicketRow(
    ticket: Ticket,
    onClick: () -> Unit
) {
    TmListRow(
        title = ticket.device.toString().ifBlank { "Unknown device" },
        subtitle = formatTimeAgo(ticket.identifier.timestampMillis()),
        onClick = onClick,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
                TmStatusBadge(ticket.status.status)
                TmPriorityBadge(ticket.priority.priority)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyReportCard(
    report: DailyReport,
    defaultPaper: PaperSize,
    onMove: (Int) -> Unit,
    onPrint: (PaperSize, (String?) -> Unit) -> Unit
) {
    TmPanel(
        modifier = Modifier.fillMaxWidth(),
        title = "End of day",
        subtitle = report.date,
        icon = Res.drawable.calendar,
        actions = {
            TmIconButton(icon = Res.drawable.chevron_left, contentDescription = "Day before", onClick = { onMove(-1) })
            TmIconButton(icon = Res.drawable.chevron_right, contentDescription = "Day after", onClick = { onMove(1) })
            PrintButton(
                templates = listOf(PrintTemplate.RECEIPT),
                defaultPaper = defaultPaper,
                onPrint = { _, paper, onResult -> onPrint(paper, onResult) }
            )
        }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
            itemVerticalAlignment = Alignment.Top
        ) {
            // Tiles share the row so a busy day shrinks them instead of wrapping one onto its own line
            val tile = Modifier.widthIn(min = 150.dp).weight(1f)

            TmStatTile(modifier = tile, label = "Taken", value = formatMoney(report.takenCents), icon = Res.drawable.dollar_sign, caption = "${report.paymentCount} payments")

            PaymentMethod.entries.filter { (report.takenByMethod[it] ?: 0) > 0 }.forEach { method ->
                TmStatTile(modifier = tile, label = formatEnumName(method.name), value = formatMoney(report.takenByMethod[method] ?: 0), icon = Res.drawable.credit_card)
            }

            TmStatTile(modifier = tile, label = "Sales", value = formatMoney(report.salesCents), icon = Res.drawable.file_text, caption = "${report.invoicesPaid} invoices paid")
            TmStatTile(modifier = tile, label = "Checked in", value = report.ticketsCheckedIn.toString(), icon = Res.drawable.log_in)
            TmStatTile(modifier = tile, label = "Picked up", value = report.ticketsPickedUp.toString(), icon = Res.drawable.log_out)
            TmStatTile(modifier = tile, label = "Waiting now", value = report.waitingForPickupNow.toString(), icon = Res.drawable.clock)
            TmStatTile(modifier = tile, label = "Overdue now", value = report.overdueNow.toString(), icon = Res.drawable.alert_circle, attention = report.overdueNow > 0)
        }
    }
}
