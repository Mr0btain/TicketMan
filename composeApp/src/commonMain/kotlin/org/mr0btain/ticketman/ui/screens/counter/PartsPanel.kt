package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.purchase.PurchaseOrder
import org.mr0btain.ticketman.data.purchase.PurchaseStatus
import org.mr0btain.ticketman.data.purchase.Supplier
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.package_icon
import ticketman.composeapp.generated.resources.truck
import kotlin.uuid.Uuid

/**
 * What the ticket card needs to order parts for a ticket
 * @param onOrder Puts some of an item on its supplier's draft order (or the one picked), calls back with what went
 * wrong or null
 */
class PartsActions(
    val suppliers: List<Supplier>,
    val onSearchParts: suspend (String) -> List<Inventory>,
    val onOrder: (item: Inventory, quantity: Int, supplier: Uuid?, onResult: (String?) -> Unit) -> Unit
)

/**
 * Parts ordered for this ticket and where each is, with a button to order another
 * @param orders Every order with a line for the ticket, null when they couldn't be read
 */
@Composable
fun PartsPanel(
    modifier: Modifier,
    ticket: Uuid,
    orders: List<PurchaseOrder>?,
    canOrder: Boolean,
    actions: PartsActions
) {
    var ordering by remember(ticket) { mutableStateOf(false) }

    if (ordering) OrderPartDialog(actions = actions, onDismiss = { ordering = false })

    val lines = orders.orEmpty().filter { it.status != PurchaseStatus.CANCELLED }.flatMap { order ->
        order.lines.filter { it.ticketIdentifier == ticket }.map { order to it }
    }

    TmPanel(
        modifier = modifier,
        title = "Parts",
        icon = Res.drawable.truck,
        actions = { if (canOrder) TmButton(text = "Order a part", onClick = { ordering = true }, variant = TmButtonVariant.SECONDARY, size = TmControlSize.SMALL) }
    ) {
        when {
            orders == null -> Text("Couldn't load parts on order", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
            lines.isEmpty() -> Text("Nothing ordered for this repair", style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
        }

        Column {
            lines.forEach { (order, line) ->
                TmListRow(
                    title = "${line.quantity} x ${line.description}",
                    subtitle = "${order.label}, " + when (order.status) {
                        PurchaseStatus.DRAFT -> "not ordered yet"
                        PurchaseStatus.RECEIVED -> "arrived"
                        else -> if (line.outstanding == 0) "arrived" else "on order from ${order.supplierName}"
                    },
                    icon = Res.drawable.package_icon
                )
            }
        }
    }
}

@Composable
private fun OrderPartDialog(actions: PartsActions, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Inventory>>(emptyList()) }
    var item by remember { mutableStateOf<Inventory?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var supplier by remember { mutableStateOf<Uuid?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        delay(300)
        matches = actions.onSearchParts(query)
    }

    val count = quantity.toIntOrNull()?.takeIf { it > 0 }
    val problem = when {
        item == null -> "Pick the part"
        count == null -> "Order at least one"
        supplier == null -> "Pick who to order it from"
        else -> null
    }

    TmDialog(
        title = "Order a part",
        description = "It goes on the supplier's draft order. When that's placed, this ticket waits for its parts.",
        onDismissRequest = onDismiss,
        actions = {
            TmButtonRow(
                confirmText = "Add to order",
                confirmEnabled = problem == null,
                loading = busy,
                onCancel = onDismiss,
                onConfirm = {
                    busy = true
                    error = null
                    actions.onOrder(item!!, count!!, supplier) { failed ->
                        busy = false
                        error = failed
                        if (failed == null) onDismiss()
                    }
                }
            )
        }
    ) {
        val picked = item
        if (picked == null) {
            TmSearchField(modifier = Modifier.fillMaxWidth(), query = query, onQueryChange = { query = it }, placeholder = "Find the part in inventory")
            Column {
                matches.forEach { match ->
                    TmListRow(
                        title = match.name,
                        icon = Res.drawable.package_icon,
                        subtitle = stockText(match.stock),
                        trailing = { TmMoneyText(match.costCents) },
                        onClick = {
                            item = match
                            supplier = match.supplierIdentifier?.takeIf { id -> actions.suppliers.any { it.identifier == id } }
                        }
                    )
                }
            }
        } else {
            TmListRow(
                title = picked.name,
                icon = Res.drawable.package_icon,
                subtitle = stockText(picked.stock),
                trailing = { TmButton(text = "Change", onClick = { item = null }, variant = TmButtonVariant.GHOST, size = TmControlSize.SMALL) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
                TmTextField(
                    modifier = Modifier.width(96.dp),
                    value = quantity,
                    onValueChange = { quantity = it.filter(Char::isDigit).take(4) },
                    label = "How many",
                    textStyle = TmTheme.typography.numeric,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TmDropdown(
                    modifier = Modifier.weight(1f),
                    label = "From",
                    options = actions.suppliers.map { it.identifier },
                    selected = supplier,
                    onSelect = { supplier = it },
                    optionLabel = { id -> actions.suppliers.firstOrNull { it.identifier == id }?.name.orEmpty() },
                    placeholder = if (actions.suppliers.isEmpty()) "Add a supplier in Purchasing" else "Pick a supplier"
                )
            }
        }

        (error ?: problem?.takeIf { item != null })?.let { TmNotice(text = it, kind = if (error != null) TmNoticeKind.ERROR else TmNoticeKind.INFO) }
    }
}
