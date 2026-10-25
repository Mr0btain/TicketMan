package org.mr0btain.ticketman.ui.screens.purchasing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.components.utils.formatDate
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.purchase.PurchaseLine
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.server.request.PurchaseOrderRequest
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.inputs.TmMoneyField
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.counter.stockText
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.arrow_left
import ticketman.composeapp.generated.resources.package_icon
import ticketman.composeapp.generated.resources.truck
import ticketman.composeapp.generated.resources.x
import kotlin.uuid.Uuid

/** A line being edited on a draft. Counts stay text so a half typed one isn't thrown away */
private class LineDraft(val line: PurchaseLine) {
    var quantity by mutableStateOf(line.quantity.toString())
    var costCents by mutableLongStateOf(line.unitCostCents)

    fun toLine(): PurchaseLine? = quantity.toIntOrNull()?.takeIf { it > 0 }?.let { line.copy(quantity = it, unitCostCents = costCents) }
}

/**
 * One purchase order. A draft is edited here and placed; a placed one shows what's still to come and takes in what
 * arrives
 * @param onSave Saves a draft, calls back with what went wrong or null
 * @param onPlace Places the draft, saving the changes passed first; calls back with what went wrong or null
 * @param onReceive Takes in a count per line, calls back with what went wrong or null
 * @param onClose Back to the list, null when the list is beside it
 */
@Composable
fun OrderDetail(
    order: PurchaseOrder,
    suppliers: List<Supplier>,
    canEdit: Boolean,
    onSearchParts: suspend (String) -> List<Inventory>,
    onSave: (PurchaseOrderRequest, (String?) -> Unit) -> Unit,
    onPlace: (PurchaseOrderRequest?, (String?) -> Unit) -> Unit,
    onReceive: (Map<Uuid, Int>, (String?) -> Unit) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClose: (() -> Unit)?
) {
    val editing = canEdit && order.status == PurchaseStatus.DRAFT
    val spacing = TmTheme.spacing

    // Rebuilt when the server sends the order back
    var supplier by remember(order) { mutableStateOf(order.supplierIdentifier) }
    var note by remember(order) { mutableStateOf(order.note) }
    val drafts = remember(order) { mutableStateListOf<LineDraft>().apply { addAll(order.lines.map { LineDraft(it) }) } }
    var busy by remember(order) { mutableStateOf(false) }
    var message by remember(order) { mutableStateOf<String?>(null) }
    var receiving by remember(order) { mutableStateOf(false) }
    var confirm by remember(order) { mutableStateOf<String?>(null) }

    val lines = drafts.map { it.toLine() }
    val linesValid = lines.all { it != null }
    val request = PurchaseOrderRequest(supplier, lines.filterNotNull(), note.trim(), order.expectedAt)
    val changed = linesValid && (supplier != order.supplierIdentifier || note.trim() != order.note || lines.filterNotNull() != order.lines)

    if (receiving) {
        ReceiveDialog(order = order, onReceive = onReceive, onDismiss = { receiving = false })
    }

    confirm?.let { what ->
        TmConfirmDialog(
            title = if (what == "delete") "Delete ${order.label}?" else "Cancel ${order.label}?",
            message = if (what == "delete") "The draft goes, nothing was sent." else "Nothing more will be received on it. What already arrived stays in stock.",
            confirmText = if (what == "delete") "Delete draft" else "Cancel order",
            destructive = true,
            onConfirm = {
                if (what == "delete") onDelete() else onCancel()
                confirm = null
            },
            onDismissRequest = { confirm = null }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        TmPanel(
            title = order.label,
            subtitle = listOfNotNull(
                order.supplierName.ifBlank { null },
                order.orderedAt?.let { "placed ${formatDate(it)}" } ?: "made ${formatDate(order.createdAt)} by ${order.createdBy}",
                order.receivedAt?.let { "all in ${formatDate(it)}" }
            ).joinToString(", "),
            icon = Res.drawable.truck,
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    PurchaseStatusBadge(order.status)
                    onClose?.let { TmIconButton(icon = Res.drawable.arrow_left, contentDescription = "Back to the orders", onClick = it, size = TmControlSize.SMALL) }
                }
            }
        ) {
            if (editing) {
                TmDropdown(
                    label = "Ordered from",
                    options = suppliers.map { it.identifier },
                    selected = supplier,
                    onSelect = { supplier = it },
                    optionLabel = { id -> suppliers.firstOrNull { it.identifier == id }?.name.orEmpty() },
                    placeholder = if (suppliers.isEmpty()) "Add a supplier first" else "Pick a supplier"
                )
            } else {
                order.supplierIdentifier?.let { id ->
                    suppliers.firstOrNull { it.identifier == id }?.let { found ->
                        val contact = listOf(found.contactName, found.phone, found.email).filter { it.isNotBlank() }.joinToString(", ")
                        if (contact.isNotBlank()) Caption(contact)
                        if (found.accountNumber.isNotBlank()) Caption("Account ${found.accountNumber}")
                    }
                }
            }

            // ------------------------------------------ Lines ------------------------------------------
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (editing) {
                    drafts.forEach { draft -> DraftLineRow(draft, onRemove = { drafts.remove(draft) }) }
                } else {
                    order.lines.forEach { line -> PlacedLineRow(line, order.status) }
                }
            }

            if (editing) {
                PartAdder(onSearch = onSearchParts) { item ->
                    drafts.add(LineDraft(PurchaseLine(inventoryIdentifier = item.identifier, description = item.name, quantity = 1, unitCostCents = item.costCents)))
                }
            }

            TmDivider()
            TmKeyValueRow(label = "Total") { TmMoneyText(lines.filterNotNull().sumOf { it.totalCents }, style = TmTheme.typography.numericLarge) }

            if (editing) {
                TmTextField(modifier = Modifier.fillMaxWidth(), value = note, onValueChange = { note = it }, label = "Note", placeholder = "For the supplier or whoever receives it")
            } else if (order.note.isNotBlank()) {
                Caption(order.note)
            }

            (message ?: if (!linesValid) "Order at least one of each part" else null)?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }

            // ------------------------------------------ Actions ------------------------------------------
            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    when (order.status) {
                        PurchaseStatus.DRAFT -> {
                            TmButton(text = "Delete", variant = TmButtonVariant.GHOST, onClick = { confirm = "delete" })
                            TmButton(
                                text = "Save",
                                variant = TmButtonVariant.SECONDARY,
                                enabled = changed && !busy,
                                onClick = {
                                    busy = true
                                    message = null
                                    onSave(request) { failed ->
                                        busy = false
                                        message = failed
                                    }
                                }
                            )
                            TmButton(
                                text = "Place order",
                                enabled = linesValid && drafts.isNotEmpty() && supplier != null,
                                loading = busy,
                                onClick = {
                                    busy = true
                                    message = null
                                    onPlace(request.takeIf { changed }) { failed ->
                                        busy = false
                                        message = failed
                                    }
                                }
                            )
                        }

                        PurchaseStatus.ORDERED, PurchaseStatus.PARTLY_RECEIVED -> {
                            TmButton(text = "Cancel order", variant = TmButtonVariant.GHOST, onClick = { confirm = "cancel" })
                            TmButton(text = "Receive", onClick = { receiving = true })
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftLineRow(draft: LineDraft, onRemove: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(draft.line.description, style = TmTheme.typography.body, color = TmTheme.colors.text)
            if (draft.line.ticketIdentifier != null) Caption("For a ticket")
        }
        TmTextField(
            modifier = Modifier.width(72.dp),
            value = draft.quantity,
            onValueChange = { draft.quantity = it.filter(Char::isDigit).take(5) },
            placeholder = "Qty",
            textStyle = TmTheme.typography.numeric,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            error = if (draft.quantity.toIntOrNull()?.let { it > 0 } == true) null else ""
        )
        TmMoneyField(modifier = Modifier.width(120.dp), cents = draft.costCents, onCentsChange = { draft.costCents = it })
        TmIconButton(icon = Res.drawable.x, contentDescription = "Remove line", onClick = onRemove, size = TmControlSize.SMALL)
    }
}

@Composable
private fun PlacedLineRow(line: PurchaseLine, status: PurchaseStatus) {
    TmListRow(
        title = "${line.quantity} x ${line.description}",
        subtitle = listOfNotNull(
            if (status == PurchaseStatus.DRAFT) null else "${line.receivedQuantity} of ${line.quantity} in",
            if (line.ticketIdentifier != null) "for a ticket" else null
        ).joinToString(", ").ifBlank { null },
        icon = Res.drawable.package_icon,
        trailing = { TmMoneyText(line.totalCents) }
    )
}

/** Searches inventory and adds the picked item as a line */
@Composable
private fun PartAdder(onSearch: suspend (String) -> List<Inventory>, onPick: (Inventory) -> Unit) {
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Inventory>>(emptyList()) }

    LaunchedEffect(query) {
        delay(300)
        matches = onSearch(query)
    }

    TmSearchField(modifier = Modifier.fillMaxWidth(), query = query, onQueryChange = { query = it }, placeholder = "Add a part from inventory")

    Column {
        matches.forEach { item ->
            TmListRow(
                title = item.name,
                icon = Res.drawable.package_icon,
                subtitle = listOf(item.sku, stockText(item.stock)).filter { it.isNotBlank() }.joinToString(", "),
                trailing = { TmMoneyText(item.costCents) },
                onClick = {
                    onPick(item)
                    query = ""
                    matches = emptyList()
                }
            )
        }
    }
}

/** How many of each line arrived, starting at everything still to come */
@Composable
private fun ReceiveDialog(order: PurchaseOrder, onReceive: (Map<Uuid, Int>, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    val counts = remember { mutableStateMapOf<Uuid, String>().apply { order.lines.forEach { put(it.identifier, it.outstanding.toString()) } } }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val parsed = order.lines.associate { it.identifier to (counts[it.identifier]?.toIntOrNull() ?: -1) }
    val problem = when {
        order.lines.any { line -> parsed.getValue(line.identifier) !in 0..line.outstanding } -> "Each count has to be from 0 to what's still to come"
        parsed.values.all { it == 0 } -> "Nothing to take in"
        else -> null
    }

    TmDialog(
        title = "Receive ${order.label}",
        description = "What arrived goes into stock now. Tickets waiting only on these parts move on.",
        onDismissRequest = onDismiss,
        actions = {
            TmButtonRow(
                confirmText = "Take into stock",
                confirmEnabled = problem == null,
                loading = busy,
                onCancel = onDismiss,
                onConfirm = {
                    busy = true
                    error = null
                    onReceive(parsed) { failed ->
                        busy = false
                        error = failed
                        if (failed == null) onDismiss()
                    }
                }
            )
        }
    ) {
        order.lines.filter { it.outstanding > 0 }.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(line.description, style = TmTheme.typography.body, color = TmTheme.colors.text)
                    Caption("${line.outstanding} still to come")
                }
                TmTextField(
                    modifier = Modifier.width(80.dp),
                    value = counts[line.identifier].orEmpty(),
                    onValueChange = { counts[line.identifier] = it.filter(Char::isDigit).take(5) },
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        (error ?: problem)?.let { TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.INFO) }
    }
}
