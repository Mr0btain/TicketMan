package org.mr0btain.ticketman.ui.screens.admin.invoices

import androidx.compose.runtime.*
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
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

/**
 * Picks the days to export invoices for, as CSV for the books
 * @param onExport Gets the first and last day, like 2026-10-01; calls back with what went wrong or null when done
 */
@Composable
fun InvoiceExportDialog(
    onExport: (from: String, to: String, onResult: (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
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
        title = "Export invoices",
        description = "A CSV of every invoice made in these days, with totals, tax and what's been paid, for a spreadsheet or the books",
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
                    onExport(start.toString(), end.toString()) { problem ->
                        exporting = false
                        error = problem
                        if (problem == null) onDismissRequest()
                    }
                }
            )
        }
    ) {
        TmSegmented(
            label = "Invoices made",
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
