package org.mr0btain.ticketman.ui.screens.counter

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
