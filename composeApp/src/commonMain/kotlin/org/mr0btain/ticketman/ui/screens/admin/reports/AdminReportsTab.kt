package org.mr0btain.ticketman.ui.screens.admin.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.components.utils.formatHours
import org.mr0btain.ticketman.components.utils.formatHoursAsDuration
import org.mr0btain.ticketman.data.invoice.PaymentMethod
import org.mr0btain.ticketman.data.report.CommissionRate
import org.mr0btain.ticketman.data.report.PeriodReport
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmStatTile
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.invoices.ExportRange
import org.mr0btain.ticketman.ui.screens.counter.PrintButton
import ticketman.composeapp.generated.resources.*

/** Width of a stat tile, they wrap onto new rows */
private val TILE_WIDTH = 180.dp

/** Width of the smaller panels */
private val PANEL_WIDTH = 360.dp

/**
 * How the shop did over some days: sales and profit, tax, money taken, techs and their commission, turnaround and
 * refunds
 * @param days The first and last day showing, null for a custom range not yet given
 * @param onShowDays Shows a custom range
 * @param onSaveCommissions Saves every user's rate, then calls back once saved
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminReportsTab(
    report: PeriodReport?,
    range: ExportRange,
    days: Pair<LocalDate, LocalDate>?,
    commissions: List<CommissionRate>,
    users: List<User>,
    defaultPaper: PaperSize,
    isBusy: Boolean,
    onPick: (ExportRange) -> Unit,
    onShowDays: (LocalDate, LocalDate) -> Unit,
    onPrint: (PaperSize, (String?) -> Unit) -> Unit,
    onSaveCommissions: (List<CommissionRate>, () -> Unit) -> Unit
) {
    val spacing = TmTheme.spacing
    var editingRates by remember { mutableStateOf(false) }

    if (editingRates) {
        CommissionDialog(
            users = users,
            rates = commissions,
            saving = isBusy,
            onSave = { rates -> onSaveCommissions(rates) { editingRates = false } },
            onDismissRequest = { editingRates = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        // ------------------------------------------ Days ------------------------------------------
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            itemVerticalAlignment = Alignment.Bottom
        ) {
            TmSegmented(
                options = ExportRange.entries,
                selected = range,
                onSelect = onPick,
                optionLabel = { it.label }
            )

            if (range == ExportRange.CUSTOM) CustomDays(days, onShowDays)

            if (report != null) {
                PrintButton(
                    templates = listOf(PrintTemplate.RECEIPT),
                    defaultPaper = defaultPaper,
                    onPrint = { _, paper, onResult -> onPrint(paper, onResult) }
                )
            }
        }

        if (report == null) {
            TmEmptyState(
                icon = Res.drawable.bar_chart,
                title = if (range == ExportRange.CUSTOM && days == null) "Pick the days" else "No report yet",
                message = if (range == ExportRange.CUSTOM && days == null) "Enter the first and last day, then show them." else "Make sure you're connected and logged in as an admin, then refresh."
            )
            return@Column
        }

        Text(text = "${report.from} to ${report.to}", style = TmTheme.typography.title, color = TmTheme.colors.text)

        // ------------------------------------------ Headline numbers ------------------------------------------
        val sales = report.sales
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmStatTile(modifier = Modifier.width(TILE_WIDTH), label = "Sales", value = formatMoney(sales.totalCents), icon = Res.drawable.file_text, caption = "${sales.invoices} invoices paid")
            TmStatTile(modifier = Modifier.width(TILE_WIDTH), label = "Net sales", value = formatMoney(sales.netCents), icon = Res.drawable.trending_up, caption = "Before tax")
            TmStatTile(
                modifier = Modifier.width(TILE_WIDTH),
                label = "Gross profit",
                value = formatMoney(sales.grossProfitCents),
                icon = Res.drawable.dollar_sign,
                caption = "Parts cost ${formatMoney(sales.partsCostCents)}"
            )
            TmStatTile(modifier = Modifier.width(TILE_WIDTH), label = "Tax collected", value = formatMoney(sales.taxCents), icon = Res.drawable.percent)
            TmStatTile(modifier = Modifier.width(TILE_WIDTH), label = "Taken", value = formatMoney(report.takenCents), icon = Res.drawable.credit_card, caption = "Less refunds")
            TmStatTile(
                modifier = Modifier.width(TILE_WIDTH),
                label = "Refunded",
                value = formatMoney(report.refunds.cents),
                icon = Res.drawable.rotate_ccw,
                caption = if (report.refunds.count == 1) "1 refund" else "${report.refunds.count} refunds",
                attention = report.refunds.count > 0
            )
        }

        if (sales.partsWithoutCost > 0) {
            TmNotice(
                text = "${sales.partsWithoutCost} part lines have no cost, so the profit counts them as free. Costs are kept from when an invoice is paid; give items a cost on the Inventory screen.",
                kind = TmNoticeKind.WARNING
            )
        }

        // ------------------------------------------ Breakdowns ------------------------------------------
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            TmPanel(modifier = Modifier.width(PANEL_WIDTH), title = "Sales", icon = Res.drawable.pie_chart) {
                TmKeyValueRow(label = "Labor", value = formatMoney(sales.laborCents), numeric = true)
                TmKeyValueRow(label = "Parts", value = formatMoney(sales.partsCents), numeric = true)
                TmKeyValueRow(label = "Other", value = formatMoney(sales.otherCents), numeric = true)
                if (sales.giftCardCents != 0L) TmKeyValueRow(label = "Gift cards sold", value = formatMoney(sales.giftCardCents), numeric = true)
                if (sales.discountCents != 0L) TmKeyValueRow(label = "Discounts", value = "-${formatMoney(sales.discountCents)}", numeric = true)
                TmKeyValueRow(label = "Tax", value = formatMoney(sales.taxCents), numeric = true)
                TmKeyValueRow(label = "Total", value = formatMoney(sales.totalCents), numeric = true)
            }

            TmPanel(modifier = Modifier.width(PANEL_WIDTH), title = "Money taken", icon = Res.drawable.credit_card) {
                val methods = PaymentMethod.entries.filter { (report.takenByMethod[it] ?: 0) != 0L }
                if (methods.isEmpty()) Text(text = "Nothing taken", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
                methods.forEach { method ->
                    TmKeyValueRow(
                        label = formatEnumName(method.name) + if (method.storedValue) " spent" else "",
                        value = formatMoney(report.takenByMethod[method] ?: 0),
                        numeric = true
                    )
                }
            }

            TmPanel(modifier = Modifier.width(PANEL_WIDTH), title = "Tax collected", icon = Res.drawable.percent) {
                if (report.taxes.isEmpty()) Text(text = "No tax charged", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
                report.taxes.forEach { tax ->
                    TmKeyValueRow(label = tax.label, value = "${formatMoney(tax.taxCents)} on ${formatMoney(tax.taxableCents)}", numeric = true)
                }
            }

            TmPanel(modifier = Modifier.width(PANEL_WIDTH), title = "Turnaround", subtitle = "Tickets picked up", icon = Res.drawable.clock) {
                val turnaround = report.turnaround
                TmKeyValueRow(label = "Picked up", value = turnaround.pickedUp.toString(), numeric = true)
                turnaround.medianHoursToReady?.let { TmKeyValueRow(label = "Median to ready", value = formatHoursAsDuration(it), numeric = true) }
                turnaround.averageHoursToReady?.let { TmKeyValueRow(label = "Average to ready", value = formatHoursAsDuration(it), numeric = true) }
                turnaround.medianHoursToPickup?.let { TmKeyValueRow(label = "Median to pickup", value = formatHoursAsDuration(it), numeric = true) }
                turnaround.averageHoursToPickup?.let { TmKeyValueRow(label = "Average to pickup", value = formatHoursAsDuration(it), numeric = true) }
            }
        }

        // ------------------------------------------ Techs ------------------------------------------
        TmPanel(
            modifier = Modifier.fillMaxWidth(),
            title = "Techs",
            subtitle = "Work logged on the invoices paid",
            icon = Res.drawable.tool,
            actions = {
                TmButton(text = "Commission rates", variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.percent, onClick = { editingRates = true })
            }
        ) {
            if (report.techs.isEmpty()) {
                Text(text = "No logged work on these invoices", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
            } else {
                val columns = listOf(
                    TmColumn("Tech", weight = 2f),
                    TmColumn("Tickets", width = 72.dp, end = true),
                    TmColumn("Hours", width = 72.dp, end = true),
                    TmColumn("Labor", width = 110.dp, end = true),
                    TmColumn("Parts", width = 110.dp, end = true),
                    TmColumn("Commission", width = 120.dp, end = true)
                )
                TmTableHeader(columns)
                report.techs.forEach { tech ->
                    TmTableRow(columns) { index ->
                        when (index) {
                            0 -> Text(text = tech.name.ifBlank { "Unknown" }, style = TmTheme.typography.body, color = TmTheme.colors.text)
                            1 -> Text(text = tech.tickets.toString(), style = TmTheme.typography.numeric, color = TmTheme.colors.text)
                            2 -> Text(text = formatHours(tech.laborHours), style = TmTheme.typography.numeric, color = TmTheme.colors.text)
                            3 -> TmMoneyText(tech.laborCents)
                            4 -> TmMoneyText(tech.partsCents)
                            else -> if (tech.commissionBasisPoints > 0) TmMoneyText(tech.commissionCents) else Text(text = "-", style = TmTheme.typography.numeric, color = TmTheme.colors.textMuted)
                        }
                    }
                }
            }
        }

        // ------------------------------------------ By day ------------------------------------------
        if (report.days.isNotEmpty()) {
            TmPanel(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(), title = "By day", icon = Res.drawable.calendar) {
                val columns = listOf(TmColumn("Day"), TmColumn("Invoices", width = 80.dp, end = true), TmColumn("Sales", width = 120.dp, end = true))
                TmTableHeader(columns)
                report.days.forEach { day ->
                    TmTableRow(columns) { index ->
                        when (index) {
                            0 -> Text(text = day.date, style = TmTheme.typography.numeric, color = TmTheme.colors.text)
                            1 -> Text(text = day.invoices.toString(), style = TmTheme.typography.numeric, color = TmTheme.colors.text)
                            else -> TmMoneyText(day.totalCents)
                        }
                    }
                }
            }
        }

        // ------------------------------------------ Refunds ------------------------------------------
        if (report.refunds.refunds.isNotEmpty()) {
            TmPanel(modifier = Modifier.fillMaxWidth(), title = "Refunds", icon = Res.drawable.rotate_ccw) {
                val columns = listOf(
                    TmColumn("Invoice", width = 160.dp),
                    TmColumn("Reason", weight = 2f),
                    TmColumn("When", width = 150.dp),
                    TmColumn("By", width = 100.dp),
                    TmColumn("Amount", width = 110.dp, end = true)
                )
                TmTableHeader(columns)
                report.refunds.refunds.forEach { refund ->
                    TmTableRow(columns) { index ->
                        when (index) {
                            0 -> Text(text = refund.invoiceLabel, style = TmTheme.typography.numeric, color = TmTheme.colors.text)
                            1 -> Text(text = refund.reason.ifBlank { formatEnumName(refund.method.name) }, style = TmTheme.typography.body, color = TmTheme.colors.text)
                            2 -> Text(text = formatDateTime(refund.receivedAt), style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
                            3 -> Text(text = refund.receivedBy, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
                            else -> TmMoneyText(refund.amountCents)
                        }
                    }
                }
            }
        }
    }
}

/** The first and last day of a custom range, shown when both are days and in order */
@Composable
private fun CustomDays(days: Pair<LocalDate, LocalDate>?, onShowDays: (LocalDate, LocalDate) -> Unit) {
    var from by remember { mutableStateOf(days?.first?.toString().orEmpty()) }
    var to by remember { mutableStateOf(days?.second?.toString().orEmpty()) }
    val start = runCatching { LocalDate.parse(from.trim()) }.getOrNull()
    val end = runCatching { LocalDate.parse(to.trim()) }.getOrNull()
    val valid = start != null && end != null && start <= end

    Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), verticalAlignment = Alignment.Bottom) {
        TmTextField(modifier = Modifier.width(140.dp), value = from, onValueChange = { from = it }, label = "From", placeholder = "2026-10-01")
        TmTextField(modifier = Modifier.width(140.dp), value = to, onValueChange = { to = it }, label = "To", placeholder = "2026-10-31")
        TmButton(text = "Show", enabled = valid, onClick = { if (start != null && end != null) onShowDays(start, end) })
    }
}
