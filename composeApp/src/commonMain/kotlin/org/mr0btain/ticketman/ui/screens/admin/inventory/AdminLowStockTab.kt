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

private val COLUMNS = listOf(
    TmColumn("Name"),
    TmColumn("Stock", width = 64.dp, end = true),
    TmColumn("Cost", width = 96.dp, end = true),
    TmColumn("Price", width = 96.dp, end = true)
)

/**
 * Inventory that's running out
 * @param onItemClick Opens the item on the main screen
 */
@Composable
fun AdminLowStockTab(
    items: List<Inventory>,
    threshold: Int,
    onThresholdChange: (Int) -> Unit,
    onItemClick: (Uuid) -> Unit
) {
    var thresholdText by remember(threshold) { mutableStateOf(threshold.toString()) }
    val colors = TmTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TmPanel(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Low stock",
            subtitle = "${items.size} items at or below $threshold, click one to open it",
            icon = Res.drawable.package_icon,
            actions = {
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
            }
        ) {
            if (items.isEmpty()) {
                TmEmptyState(icon = Res.drawable.check_circle, title = "Nothing is running low", message = "No item has $threshold or fewer in stock.")
                return@TmPanel
            }

            Column {
                TmTableHeader(COLUMNS)

                items.forEach { item ->
                    TmTableRow(COLUMNS, onClick = { onItemClick(item.identifier) }) { column ->
                        when (column) {
                            0 -> Column {
                                Text(item.name, style = TmTheme.typography.label, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (item.description.isNotBlank()) {
                                    Text(item.description, style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            1 -> Text(
                                text = item.stock.toString(),
                                style = TmTheme.typography.numeric,
                                color = if (item.stock <= 0) colors.danger.color else colors.warning.color
                            )
                            2 -> TmMoneyText(item.costCents)
                            3 -> TmMoneyText(item.priceCents)
                        }
                    }
                }
            }
        }
    }
}
