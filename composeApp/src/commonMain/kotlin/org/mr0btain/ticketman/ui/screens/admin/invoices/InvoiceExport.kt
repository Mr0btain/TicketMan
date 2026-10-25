package org.mr0btain.ticketman.ui.screens.admin.invoices

import androidx.compose.runtime.*
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.data.invoice.ExportFormat
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import kotlin.time.Clock

/** The ranges offered for the invoice export */
enum class ExportRange(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    THIS_YEAR("This year"),
    LAST_YEAR("Last year"),
    CUSTOM("Days");

    /** First and last day, both included, for any but [CUSTOM] */
    fun days(today: LocalDate): Pair<LocalDate, LocalDate>? {
        val monthStart = LocalDate(today.year, today.month, 1)

        return when (this) {
            THIS_MONTH -> monthStart to today
            LAST_MONTH -> monthStart.minus(DatePeriod(months = 1)) to monthStart.minus(DatePeriod(days = 1))
            THIS_YEAR -> LocalDate(today.year, 1, 1) to today
            LAST_YEAR -> LocalDate(today.year - 1, 1, 1) to LocalDate(today.year - 1, 12, 31)
            CUSTOM -> null
        }
    }
}

/** What each export format holds, under the picker */
private fun ExportFormat.description(): String = when (this) {
    ExportFormat.INVOICES -> "Every invoice made in these days, with totals, tax and what's been paid, for a spreadsheet"
    ExportFormat.QUICKBOOKS -> "Invoices paid in these days, a row per line, for QuickBooks Online's invoice import. Item names are set on the Server tab"
    ExportFormat.XERO -> "Invoices paid in these days, a row per line, for Xero's sales invoice import. Accounts and tax rates are set on the Server tab"
    ExportFormat.PAYMENTS -> "Every payment and refund taken in these days, for matching against the bank"
}

/**
 * Picks what to export and the days, as CSV for the books
 * @param onExport Gets the first and last day, like 2026-10-01, and the format; calls back with what went wrong or
 * null when done
 */
@Composable
fun InvoiceExportDialog(
    onExport: (from: String, to: String, format: ExportFormat, onResult: (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    var format by remember { mutableStateOf(ExportFormat.INVOICES) }
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    var range by remember { mutableStateOf(ExportRange.THIS_MONTH) }
    var from by remember { mutableStateOf(LocalDate(today.year, today.month, 1).toString()) }
    var to by remember { mutableStateOf(today.toString()) }
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val days = range.days(today) ?: run {
        val start = runCatching { LocalDate.parse(from.trim()) }.getOrNull()
        val end = runCatching { LocalDate.parse(to.trim()) }.getOrNull()
        if (start != null && end != null && start <= end) start to end else null
    }

    TmDialog(
        title = "Export for the books",
        description = format.description(),
        onDismissRequest = onDismissRequest,
        actions = {
            TmButtonRow(
                confirmText = "Save CSV",
                confirmEnabled = days != null,
                loading = exporting,
                onCancel = onDismissRequest,
                onConfirm = {
                    val (start, end) = days ?: return@TmButtonRow
                    exporting = true
                    error = null
                    onExport(start.toString(), end.toString(), format) { problem ->
                        exporting = false
                        error = problem
                        if (problem == null) onDismissRequest()
                    }
                }
            )
        }
    ) {
        TmSegmented(
            label = "Export",
            options = ExportFormat.entries,
            selected = format,
            onSelect = { format = it },
            optionLabel = { it.label }
        )

        TmSegmented(
            label = if (format == ExportFormat.INVOICES) "Invoices made" else if (format == ExportFormat.PAYMENTS) "Taken" else "Invoices paid",
            options = ExportRange.entries,
            selected = range,
            onSelect = { range = it },
            optionLabel = { it.label }
        )

        if (range == ExportRange.CUSTOM) {
            TmFieldPair(
                { TmTextField(modifier = it, value = from, onValueChange = { v -> from = v }, label = "From", placeholder = "2026-10-01") },
                { TmTextField(modifier = it, value = to, onValueChange = { v -> to = v }, label = "To", placeholder = "2026-10-31") }
            )
            if (days == null) TmNotice(text = "Enter both days like 2026-10-01, the first one first", kind = TmNoticeKind.WARNING)
        } else {
            days?.let { (start, end) -> TmNotice(text = "$start to $end", kind = TmNoticeKind.INFO) }
        }

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}
