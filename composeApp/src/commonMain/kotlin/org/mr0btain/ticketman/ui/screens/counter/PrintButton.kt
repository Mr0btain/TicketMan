package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.screens.counter.components.CounterPrinter
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.data.TmPrintPreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import org.mr0btain.ticketman.components.print.PrintTemplate
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.printer
import ticketman.composeapp.generated.resources.download

/**
 * Print button that asks which slip and which paper, starting on the shop's default paper
 * @param templates The slips that make sense here, the first is picked to start with
 * @param onPrint Prints it, calls back with what went wrong or null if it went to the printer
 */
@Composable
fun PrintButton(
    label: String = "Print",
    templates: List<PrintTemplate>,
    defaultPaper: PaperSize,
    size: TmControlSize = TmControlSize.MEDIUM,
    onPrint: (PrintTemplate, PaperSize, (String?) -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }

    TmButton(
        text = label,
        onClick = { open = true },
        variant = TmButtonVariant.SECONDARY,
        size = size,
        leadingIcon = Res.drawable.printer
    )

    if (!open) return

    var template by remember { mutableStateOf(templates.first()) }
    var paper by remember { mutableStateOf(defaultPaper) }
    var printing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    TmDialog(
        title = "Print",
        onDismissRequest = { open = false },
        actions = {
            TmButtonRow(
                confirmText = "Print ${template.label.lowercase()}",
                loading = printing,
                onCancel = { open = false },
                onConfirm = {
                    printing = true
                    error = null
                    onPrint(template, paper) { problem ->
                        printing = false
                        error = problem
                        if (problem == null) open = false
                    }
                }
            )
        }
    ) {
        if (templates.size > 1) {
            TmSegmented(
                label = "What to print",
                options = templates,
                selected = template,
                onSelect = { template = it },
                optionLabel = { it.label }
            )
        }

        TmSegmented(
            label = "Paper",
            options = PaperSize.entries,
            selected = paper,
            onSelect = { paper = it },
            optionLabel = { it.label }
        )

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}

/** Saves the invoice as a PDF from the server, to email to the customer or keep */
@Composable
fun SavePdfButton(size: TmControlSize = TmControlSize.LARGE, onClick: () -> Unit) {
    TmButton(
        text = "Save PDF",
        onClick = onClick,
        variant = TmButtonVariant.SECONDARY,
        size = size,
        leadingIcon = Res.drawable.download
    )
}

/**
 * The counter's invoice, when one is open to preview: as it will print, on the paper picked, with printing it and saving
 * it as a PDF. Paper starts on letter, what the PDF is laid out for
 */
@Composable
fun InvoicePreviewDialog(printer: CounterPrinter) {
    val preview by printer.invoicePreview.collectAsState()
    val open = preview ?: return

    var paper by remember(open) { mutableStateOf(PaperSize.LETTER) }
    var printing by remember(open) { mutableStateOf(false) }
    var error by remember(open) { mutableStateOf<String?>(null) }

    TmDialog(
        title = open.invoice.label,
        description = "As it will print",
        maxWidth = 720.dp,
        onDismissRequest = { printer.closeInvoicePreview() },
        actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm, Alignment.End)) {
                TmButton(text = "Close", onClick = { printer.closeInvoicePreview() }, variant = TmButtonVariant.GHOST)
                SavePdfButton(size = TmControlSize.MEDIUM) { printer.savePdf(open.invoice) }
                TmButton(
                    text = "Print",
                    loading = printing,
                    leadingIcon = Res.drawable.printer,
                    onClick = {
                        printing = true
                        error = null
                        printer.printInvoicePreview(paper) { problem ->
                            printing = false
                            error = problem
                        }
                    }
                )
            }
        }
    ) {
        TmSegmented(
            label = "Paper",
            options = INVOICE_PAPERS,
            selected = paper,
            onSelect = { paper = it },
            optionLabel = { it.label }
        )

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

        TmPrintPreview(
            document = open.document,
            paper = paper,
            modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
        )
    }
}

// A label is too small for an invoice
private val INVOICE_PAPERS = listOf(PaperSize.LETTER, PaperSize.RECEIPT_80MM)
