package org.mr0btain.ticketman.ui.screens.admin.server

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import org.mr0btain.ticketman.components.data.importing.ImportField
import org.mr0btain.ticketman.components.data.importing.ImportKind
import org.mr0btain.ticketman.components.data.importing.ImportSheet
import org.mr0btain.ticketman.server.response.ImportOutcome
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.components.ImportPreview
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.upload

/** Most rows the preview lists, the counts cover all of them */
private const val PREVIEW_ROWS = 100

/**
 * Picking a CSV of customers or inventory from another system to import
 * @param onPicked Gets what it's for and the file picked
 */
@Composable
fun ImportCard(
    isBusy: Boolean,
    onPicked: (ImportKind, PlatformFile) -> Unit,
    modifier: Modifier = Modifier
) {
    var kind by remember { mutableStateOf(ImportKind.CUSTOMERS) }
    val picker = rememberFilePickerLauncher(type = FileKitType.File(listOf("csv", "txt"))) { file -> file?.let { onPicked(kind, it) } }

    TmPanel(
        modifier = modifier,
        title = "Import",
        subtitle = "Customers or inventory from another system",
        icon = Res.drawable.upload
    ) {
        Text(
            "A CSV with a header row, as spreadsheets save it. You'll see what would be added, and what's already here, before anything is.",
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
            ImportKind.entries.forEach { option ->
                TmButton(
                    text = "Import ${option.label.lowercase()}",
                    variant = TmButtonVariant.SECONDARY,
                    enabled = !isBusy,
                    onClick = {
                        kind = option
                        picker.launch()
                    }
                )
            }
        }
    }
}

/**
 * Matching a file's columns to fields and checking what importing it would do, row by row, before doing it
 * @param preview Null while the server works it out
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportDialog(
    sheet: ImportSheet,
    mapping: Map<ImportField, Int?>,
    preview: ImportPreview?,
    problem: String?,
    importing: Boolean,
    onMap: (ImportField, Int?) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = TmTheme.colors
    val columns = remember(sheet.kind) {
        listOf(
            TmColumn("Line", width = 44.dp),
            TmColumn(if (sheet.kind == ImportKind.CUSTOMERS) "Name, phone, email" else "Name, price, stock", weight = 1.3f),
            TmColumn("", width = 112.dp),
            TmColumn("Why", weight = 1f)
        )
    }

    TmDialog(
        title = "Import ${sheet.kind.plural}",
        description = "${sheet.rows.size} rows in the file. Check each field comes from the right column, then look over the preview.",
        onDismissRequest = onDismiss,
        maxWidth = 960.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = preview?.let { "Import ${sheet.kind.count(it.toAdd)}" } ?: "Import",
                confirmEnabled = preview != null && preview.toAdd > 0,
                loading = importing,
                onCancel = onDismiss,
                onConfirm = onImport
            )
        }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)
        ) {
            ImportField.of(sheet.kind).forEach { field ->
                TmDropdown(
                    modifier = Modifier.width(160.dp),
                    label = field.label,
                    options = listOf(null) + sheet.headers.indices.toList(),
                    selected = mapping[field],
                    onSelect = { onMap(field, it) },
                    optionLabel = { column -> column?.let { sheet.headers[it] } ?: "Not in the file" },
                    placeholder = "Not in the file"
                )
            }
        }
        if (sheet.kind == ImportKind.CUSTOMERS) {
            Text(
                "Without a Name column, the name is First name and Last name put together. Customers with a phone number or email already here are skipped.",
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
        } else {
            Text(
                "Items with a name already in inventory are skipped. A blank stock means the item isn't counted.",
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
        }

        when {
            problem != null -> TmNotice(text = problem, kind = TmNoticeKind.ERROR)
            preview == null -> Text("Checking with the server", style = TmTheme.typography.body, color = colors.textMuted)
            else -> TmNotice(
                kind = if (preview.toAdd > 0) TmNoticeKind.INFO else TmNoticeKind.WARNING,
                text = "${preview.toAdd} to add, ${preview.matched} already here, ${preview.failed} with problems"
            )
        }

        preview?.let {
            Column {
                TmTableHeader(columns)
                preview.rows.take(PREVIEW_ROWS).forEach { row ->
                    TmTableRow(columns) { column ->
                        when (column) {
                            0 -> Text(row.line.toString(), style = TmTheme.typography.numeric, color = colors.textMuted)
                            1 -> Column {
                                Text(row.details.name.ifBlank { "-" }, style = TmTheme.typography.body, color = colors.text, maxLines = 1)
                                val detail = if (sheet.kind == ImportKind.CUSTOMERS) row.details.contact
                                    else listOfNotNull(
                                        row.details.priceCents?.let { formatMoney(it) },
                                        row.details.stock?.let { if (it == Int.MAX_VALUE) "not counted" else "$it in stock" }
                                    ).joinToString(", ")
                                if (detail.isNotBlank()) Text(detail, style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 1)
                            }
                            2 -> when (row.outcome) {
                                ImportOutcome.ADD -> TmBadge(text = "Add", tone = colors.success)
                                ImportOutcome.MATCH -> TmBadge(text = "Already here", tone = colors.neutral)
                                ImportOutcome.ERROR -> TmBadge(text = "Problem", tone = colors.danger)
                            }
                            3 -> Text(row.message, style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 2)
                        }
                    }
                }
                if (preview.rows.size > PREVIEW_ROWS) {
                    Text(
                        "And ${preview.rows.size - PREVIEW_ROWS} more rows, counted above",
                        style = TmTheme.typography.caption,
                        color = colors.textFaint
                    )
                }
            }
        }
    }
}
