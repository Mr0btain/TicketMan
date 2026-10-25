package org.mr0btain.ticketman.ui.screens.admin.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.check_circle
import ticketman.composeapp.generated.resources.package_icon
import kotlin.uuid.Uuid

private val NARROW_COLUMNS = listOf(
    TmColumn("Item"),
    TmColumn("Stock", width = 56.dp, end = true),
    TmColumn("Reorder at", width = 80.dp, end = true)
)

private val COLUMNS = listOf(
    TmColumn("Name"),
    TmColumn("Stock", width = 64.dp, end = true),
    TmColumn("Reorder at", width = 88.dp, end = true),
    TmColumn("Cost", width = 96.dp, end = true),
    TmColumn("Price", width = 96.dp, end = true)
)

/**
 * Inventory that's running out: at or below its own reorder point, or the threshold for items without one
 * @param onItemClick Opens the item on the main screen
 * @param onDraftOrders Drafts purchase orders for these, one per supplier; calls back with what went wrong or null
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AdminLowStockTab(
    items: List<Inventory>,
    threshold: Int,
    onThresholdChange: (Int) -> Unit,
    onItemClick: (Uuid) -> Unit,
    onDraftOrders: (((String?) -> Unit) -> Unit)? = null
) {
    var drafting by remember { mutableStateOf(false) }
    var thresholdText by remember(threshold) { mutableStateOf(threshold.toString()) }
    val colors = TmTheme.colors

    val controls: @Composable () -> Unit = {
        TmTextField(
            modifier = Modifier.width(100.dp),
            value = thresholdText,
            onValueChange = { text -> thresholdText = text.filter { it.isDigit() }.take(5) },
            placeholder = "Threshold",
            textStyle = TmTheme.typography.numeric,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        TmButton(
            text = "Set threshold",
            variant = TmButtonVariant.SECONDARY,
            enabled = thresholdText.toIntOrNull()?.let { it != threshold } == true,
            onClick = { thresholdText.toIntOrNull()?.let(onThresholdChange) }
        )

        // Enough of each to get back to twice its reorder point, on a draft per supplier
        if (onDraftOrders != null && items.isNotEmpty()) {
            TmButton(
                text = "Draft orders",
                loading = drafting,
                onClick = {
                    drafting = true
                    onDraftOrders { drafting = false }
                }
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Narrow: the controls go under the title, and only the name, stock and reorder point are shown
        val narrow = maxWidth < TABLE_WIDTH
        val columns = if (narrow) NARROW_COLUMNS else COLUMNS

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            TmPanel(
                modifier = Modifier.widthIn(max = 900.dp),
                title = "Low stock",
                subtitle = "${items.size} items at their reorder point, or $threshold for items without one. Click one to open it",
                icon = Res.drawable.package_icon,
                actions = { if (!narrow) controls() }
            ) {
                if (narrow) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)
                    ) { controls() }
                }

                if (items.isEmpty()) {
                    TmEmptyState(icon = Res.drawable.check_circle, title = "Nothing is running low", message = "No item is at its reorder point.")
                    return@TmPanel
                }

                Column {
                    TmTableHeader(columns)

                    items.forEach { item ->
                        TmTableRow(columns, onClick = { onItemClick(item.identifier) }) { column ->
                            when (column) {
                                0 -> Column {
                                    Text(item.name, style = TmTheme.typography.label, color = colors.text, maxLines = if (narrow) 2 else 1, overflow = TextOverflow.Ellipsis)
                                    val detail = if (narrow) listOf(item.sku, item.description).firstOrNull { it.isNotBlank() }.orEmpty() else item.description
                                    if (detail.isNotBlank()) {
                                        Text(detail, style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                1 -> Text(
                                    text = item.stock.toString(),
                                    style = TmTheme.typography.numeric,
                                    color = if (item.stock <= 0) colors.danger.color else colors.warning.color
                                )
                                2 -> Text(
                                    text = (item.reorderPoint ?: threshold).toString(),
                                    style = TmTheme.typography.numeric,
                                    color = if (item.reorderPoint == null) colors.textFaint else colors.textMuted
                                )
                                3 -> TmMoneyText(item.costCents)
                                4 -> TmMoneyText(item.priceCents)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Narrower than this, cost and price are left off */
private val TABLE_WIDTH = 600.dp
