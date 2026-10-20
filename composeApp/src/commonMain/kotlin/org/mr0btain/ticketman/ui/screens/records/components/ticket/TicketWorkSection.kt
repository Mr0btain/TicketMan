package org.mr0btain.ticketman.ui.screens.records.components.ticket

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.utils.formatDateTime
import org.mr0btain.ticketman.components.utils.formatQuantity
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.WorkEntry
import org.mr0btain.ticketman.data.ticket.WorkType
import org.mr0btain.ticketman.server.request.WorkRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.formatMoney
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.search.TmSearchBar
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.records.components.DetailSection
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.x
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.uuid.Uuid

/**
 * The ticket's work log in its details: each entry with who logged it and what it adds to the invoice
 * @param entries Null while loading
 * @param canChange Whether work can be logged or taken off: not for restricted users, or once it's picked up
 */
@Composable
fun TicketWorkSection(
    entries: List<WorkEntry>?,
    loadError: String?,
    canChange: Boolean,
    onLog: () -> Unit,
    onRemove: (WorkEntry) -> Unit
) {
    val colors = TmTheme.colors
    var removing by remember { mutableStateOf<WorkEntry?>(null) }

    removing?.let { entry ->
        TmConfirmDialog(
            title = "Remove ${entry.description}?",
            message = "Its line comes off the ticket's invoice too.",
            confirmText = "Remove",
            destructive = true,
            onConfirm = {
                onRemove(entry)
                removing = null
            },
            onDismissRequest = { removing = null }
        )
    }

    DetailSection("Work") {
        when {
            loadError != null -> TmNotice(kind = TmNoticeKind.ERROR, text = "Couldn't load the work log: $loadError")
            entries == null -> Text("Loading", style = TmTheme.typography.caption, color = colors.textMuted)
            entries.isEmpty() -> Text("Nothing logged yet", style = TmTheme.typography.body, color = colors.textMuted)
            else -> Column {
                entries.forEach { entry ->
                    TmListRow(
                        title = entry.description,
                        subtitle = listOf(
                            if (entry.type == WorkType.LABOR) "${formatQuantity(entry.quantity)} h at ${formatMoney(entry.unitPriceCents)}"
                            else "${formatQuantity(entry.quantity)} x ${formatMoney(entry.unitPriceCents)}",
                            entry.userName,
                            formatDateTime(entry.workedAt)
                        ).filter { it.isNotBlank() }.joinToString("  ·  "),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                                TmMoneyText(entry.totalCents)
                                if (canChange) {
                                    TmIconButton(
                                        icon = Res.drawable.x,
                                        contentDescription = "Remove this work",
                                        onClick = { removing = entry },
                                        size = TmControlSize.SMALL
                                    )
                                }
                            }
                        }
                    )
                }
                TmKeyValueRow(label = "Work total") { TmMoneyText(entries.sumOf { it.totalCents }) }
            }
        }

        if (canChange) {
            TmButton(
                text = "Log work",
                onClick = onLog,
                variant = TmButtonVariant.SECONDARY,
                size = TmControlSize.SMALL,
                leadingIcon = Res.drawable.plus
            )
        }
    }
}

/**
 * Logs time or a part on a ticket. The price starts at the shop's labor rate, or the part's price, and can be changed
 * @param laborRateCents The shop profile's rate per hour
 * @param onFindPart Loads a part picked in the search, for its price and stock
 * @param onSave Calls back with what went wrong, or null once it's saved
 */
@Composable
fun WorkDialog(
    laborRateCents: Long,
    onPartSearch: suspend (String) -> List<SearchResult>,
    onFindPart: suspend (Uuid) -> Inventory?,
    onSave: (WorkRequest, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    var type by remember { mutableStateOf(WorkType.LABOR) }
    var description by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    var rateCents by remember { mutableLongStateOf(laborRateCents) }
    var part by remember { mutableStateOf<Inventory?>(null) }
    var partPriceCents by remember { mutableLongStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val quantity = (if (type == WorkType.LABOR) hours else count).trim().toDoubleOrNull()
    val price = if (type == WorkType.LABOR) rateCents else partPriceCents
    val problem = when {
        type == WorkType.LABOR && description.isBlank() -> "Say what was done"
        type == WorkType.PART && part == null -> "Pick the part"
        quantity == null || quantity <= 0 -> if (type == WorkType.LABOR) "Enter the hours, like 1.5" else "Enter how many"
        type == WorkType.PART && quantity % 1.0 != 0.0 -> "Parts are counted in whole numbers"
        abs(quantity * 100 - (quantity * 100).roundToLong()) > 1e-6 -> "Two decimals at most"
        else -> null
    }

    TmDialog(
        title = "Log work",
        onDismissRequest = onDismissRequest,
        maxWidth = 560.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = "Log it",
                confirmEnabled = problem == null,
                loading = saving,
                onCancel = onDismissRequest,
                onConfirm = {
                    saving = true
                    error = null
                    onSave(
                        WorkRequest(
                            type = type,
                            description = description.trim(),
                            quantity = quantity ?: 0.0,
                            unitPriceCents = price,
                            inventoryIdentifier = part?.identifier.takeIf { type == WorkType.PART }
                        )
                    ) { failed ->
                        saving = false
                        error = failed
                        if (failed == null) onDismissRequest()
                    }
                }
            )
        }
    ) {
        TmSegmented(
            options = WorkType.entries,
            selected = type,
            onSelect = {
                type = it
                error = null
            },
            optionLabel = { if (it == WorkType.LABOR) "Time" else "Part" }
        )

        if (type == WorkType.LABOR) {
            TmTextField(modifier = Modifier.fillMaxWidth(), value = description, onValueChange = { description = it }, label = "What was done")
            TmFieldPair(
                { TmTextField(modifier = it, value = hours, onValueChange = { v -> hours = v }, label = "Hours", placeholder = "1.5") },
                { TmMoneyField(modifier = it, cents = rateCents, onCentsChange = { v -> rateCents = v }, label = "Per hour", supportingText = if (laborRateCents == 0L) "Set the shop's rate on the admin Server tab" else null) }
            )
        } else {
            TmSearchBar(
                modifier = Modifier.fillMaxWidth(),
                label = "Part",
                placeholder = "Search inventory",
                initialQuery = part?.name.orEmpty(),
                onQuery = onPartSearch,
                onPick = { result ->
                    scope.launch {
                        val item = onFindPart(result.identifier)
                        if (item == null) {
                            error = "Couldn't load that part"
                            return@launch
                        }
                        part = item
                        partPriceCents = item.priceCents
                        if (description.isBlank()) description = item.name
                    }
                }
            )
            part?.let { item ->
                if (item.stock != Int.MAX_VALUE) {
                    Text(
                        "${item.stock} in stock, taken when the ticket is picked up",
                        style = TmTheme.typography.caption,
                        color = if (item.stock <= 0) TmTheme.colors.danger.color else TmTheme.colors.textMuted
                    )
                }
            }
            TmTextField(modifier = Modifier.fillMaxWidth(), value = description, onValueChange = { description = it }, label = "On the invoice as", placeholder = part?.name ?: "")
            TmFieldPair(
                { TmTextField(modifier = it, value = count, onValueChange = { v -> count = v }, label = "How many") },
                { TmMoneyField(modifier = it, cents = partPriceCents, onCentsChange = { v -> partPriceCents = v }, label = "Each") }
            )
        }

        if (quantity != null && quantity > 0) {
            TmKeyValueRow(label = "Adds to the invoice") { TmMoneyText((quantity * price).roundToLong()) }
        }

        (error ?: problem.takeIf { description.isNotEmpty() || hours.isNotEmpty() || part != null })?.let {
            TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.WARNING)
        }
    }
}
