package org.mr0btain.ticketman.ui.screens.admin.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.ui.screens.admin.components.EmptyMessage
import org.mr0btain.ticketman.ui.screens.admin.components.SectionCard
import kotlin.uuid.Uuid

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        SectionCard(
            modifier = Modifier.widthIn(max = 900.dp),
            title = "Low Stock (${items.size})",
            action = {
                OutlinedTextField(
                    modifier = Modifier.width(140.dp),
                    value = thresholdText,
                    onValueChange = { text ->
                        thresholdText = text.filter { it.isDigit() }.take(5)
                    },
                    label = { Text("Threshold") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.padding(4.dp))

                Button(
                    enabled = thresholdText.toIntOrNull()?.let { it != threshold } == true,
                    onClick = { thresholdText.toIntOrNull()?.let(onThresholdChange) }
                ) {
                    Text("Apply")
                }
            }
        ) {
            if (items.isEmpty()) {
                EmptyMessage("Nothing is at or below $threshold in stock")
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Name", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("Stock", Modifier.width(80.dp), fontWeight = FontWeight.SemiBold)
                    Text("Cost", Modifier.width(90.dp), fontWeight = FontWeight.SemiBold)
                    Text("Price", Modifier.width(90.dp), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(80.dp))
                }

                HorizontalDivider()
            }

            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.description.isNotBlank()) {
                            Text(item.description, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Text(
                        modifier = Modifier.width(80.dp),
                        text = item.stock.toString(),
                        fontWeight = FontWeight.Bold,
                        color = if (item.stock <= 0) MaterialTheme.colorScheme.error else LocalContentColor.current
                    )
                    Text(Money.format(item.costCents), Modifier.width(90.dp))
                    Text(Money.format(item.priceCents), Modifier.width(90.dp))

                    TextButton(
                        modifier = Modifier.width(80.dp),
                        onClick = { onItemClick(item.identifier) }
                    ) {
                        Text("Open")
                    }
                }
            }
        }
    }
}
