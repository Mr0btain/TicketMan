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
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
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
 * A part added in [WorkDialog], before it's logged
 * @param count As typed
 */
data class PartLine(val item: Inventory, val count: String = "1", val priceCents: Long = item.priceCents)

/**
 * What [WorkDialog] logs: the time if any was filled in, then each part, or what's wrong with it
 * @return The requests, or the problem with them
 */
fun workRequests(description: String, hours: String, rateCents: Long, parts: List<PartLine>): Pair<List<WorkRequest>, String?> {
    val requests = mutableListOf<WorkRequest>()

    if (description.isNotBlank() || hours.isNotBlank()) {
        val quantity = hours.trim().toDoubleOrNull()
        when {
            description.isBlank() -> return requests to "Say what was done"
            quantity == null || quantity <= 0 -> return requests to "Enter the hours, like 1.5"
            !twoDecimals(quantity) -> return requests to "Hours can have two decimals at most"
        }
        requests += WorkRequest(WorkType.LABOR, description.trim(), quantity = quantity, unitPriceCents = rateCents)
    }

    parts.forEach { line ->
        val count = line.count.trim().toDoubleOrNull()
        if (count == null || count <= 0 || count % 1.0 != 0.0) return requests to "Enter how many of ${line.item.name}, in whole numbers"
        requests += WorkRequest(WorkType.PART, line.item.name, quantity = count, unitPriceCents = line.priceCents, inventoryIdentifier = line.item.identifier)
    }

    return requests to if (requests.isEmpty()) "Fill in the time or add a part" else null
}

private fun twoDecimals(quantity: Double): Boolean = abs(quantity * 100 - (quantity * 100).roundToLong()) <= 1e-6

/**
 * Logs the time and parts for a repair together: the time is optional, and any number of parts can be added to the
 * list before it's all logged at once. Prices start at the shop's labor rate and each part's price, and can be changed
 * @param laborRateCents The shop profile's rate per hour
 * @param onFindPart Loads a part picked in the search, for its price and stock
 * @param onSave Calls back with what went wrong, or null once it's all saved
 */
@Composable
fun WorkDialog(
    laborRateCents: Long,
    onPartSearch: suspend (String) -> List<SearchResult>,
    onFindPart: suspend (Uuid) -> Inventory?,
    onSave: (List<WorkRequest>, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit,
    initialParts: List<PartLine> = emptyList()
) {
    var description by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var rateCents by remember { mutableLongStateOf(laborRateCents) }
    var parts by remember { mutableStateOf(initialParts) }
    // Bumped on each pick so the search starts empty again
    var picks by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val (requests, problem) = workRequests(description, hours, rateCents, parts)
    val started = description.isNotEmpty() || hours.isNotEmpty() || parts.isNotEmpty()

    TmDialog(
        title = "Log work",
        onDismissRequest = onDismissRequest,
        maxWidth = 600.dp,
        dismissOnOutsideClick = false,
        actions = {
            TmButtonRow(
                confirmText = if (requests.size > 1) "Log all ${requests.size}" else "Log it",
                confirmEnabled = problem == null,
                loading = saving,
                onCancel = onDismissRequest,
                onConfirm = {
                    saving = true
                    error = null
                    onSave(requests) { failed ->
                        saving = false
                        error = failed
                        if (failed == null) onDismissRequest()
                    }
                }
            )
        }
    ) {
        TmSectionLabel("Time")
        TmTextField(modifier = Modifier.fillMaxWidth(), value = description, onValueChange = { description = it }, label = "What was done", placeholder = "Leave blank to log only parts")
        TmFieldPair(
            { TmTextField(modifier = it, value = hours, onValueChange = { v -> hours = v }, label = "Hours", placeholder = "1.5") },
            { TmMoneyField(modifier = it, cents = rateCents, onCentsChange = { v -> rateCents = v }, label = "Per hour", supportingText = if (laborRateCents == 0L) "Set the shop's rate on the admin Server tab" else null) }
        )

        TmSectionLabel("Parts")
        key(picks) {
            TmSearchBar(
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search inventory to add a part",
                onQuery = onPartSearch,
                onPick = { result ->
                    scope.launch {
                        val item = onFindPart(result.identifier)
                        picks++
                        if (item == null) {
                            error = "Couldn't load that part"
                            return@launch
                        }
                        error = null
                        // The same part again counts one more
                        val existing = parts.indexOfFirst { it.item.identifier == item.identifier }
                        parts = if (existing < 0) parts + PartLine(item)
                        else parts.mapIndexed { i, line ->
                            if (i == existing) line.copy(count = ((line.count.trim().toIntOrNull() ?: 0) + 1).toString()) else line
                        }
                    }
                }
            )
        }

        if (parts.isEmpty()) {
            Text("No parts added", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                parts.forEachIndexed { index, line ->
                    PartLineRow(
                        line = line,
                        onChange = { changed -> parts = parts.mapIndexed { i, old -> if (i == index) changed else old } },
                        onRemove = { parts = parts.filterIndexed { i, _ -> i != index } }
                    )
                }
            }
        }

        if (requests.isNotEmpty()) {
            TmKeyValueRow(label = "Adds to the invoice") { TmMoneyText(requests.sumOf { (it.quantity * (it.unitPriceCents ?: 0)).roundToLong() }) }
        }

        (error ?: problem.takeIf { started })?.let {
            TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.WARNING)
        }
    }
}

// One part in the work dialog's list: its name and stock, how many and the price each
@Composable
private fun PartLineRow(line: PartLine, onChange: (PartLine) -> Unit, onRemove: () -> Unit) {
    val colors = TmTheme.colors
    val count = line.count.trim().toIntOrNull()
    val stocked = line.item.stock != Int.MAX_VALUE

    val name: @Composable () -> Unit = {
        Column {
            Text(line.item.name, style = TmTheme.typography.body, color = colors.text)
            if (stocked) {
                Text(
                    "${line.item.stock} in stock, taken at pickup",
                    style = TmTheme.typography.caption,
                    color = if (line.item.stock <= 0 || (count != null && count > line.item.stock)) colors.danger.color else colors.textMuted
                )
            }
        }
    }

    val fields: @Composable RowScope.() -> Unit = {
        TmTextField(
            modifier = Modifier.width(72.dp),
            value = line.count,
            onValueChange = { onChange(line.copy(count = it)) },
            label = "How many",
            size = TmControlSize.SMALL
        )
        TmMoneyField(
            modifier = Modifier.width(112.dp),
            cents = line.priceCents,
            onCentsChange = { onChange(line.copy(priceCents = it)) },
            label = "Each",
            size = TmControlSize.SMALL
        )
        TmIconButton(
            icon = Res.drawable.x,
            contentDescription = "Take ${line.item.name} off the list",
            onClick = onRemove,
            size = TmControlSize.SMALL
        )
    }

    BoxWithConstraints {
        // Narrow: the name on its own line, the fields under it
        if (maxWidth < PART_ROW_WIDTH) {
            Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
                name()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), content = fields)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                Box(Modifier.weight(1f)) { name() }
                fields()
            }
        }
    }
}

/** Narrower than this, a part's fields go under its name */
private val PART_ROW_WIDTH = 440.dp
