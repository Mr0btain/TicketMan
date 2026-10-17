package org.mr0btain.ticketman.ui.screens.counter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.money.Money
import org.mr0btain.ticketman.ui.composables.EmptyMessage
import org.mr0btain.ticketman.ui.composables.SectionCard

/**
 * Look up what a part costs and whether it's in stock
 */
@Composable
fun PriceCheck(
    results: List<Inventory>,
    onSearch: (String) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(text = "Price Check", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .focusRequester(focus),
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            label = { Text("Part name or description") },
            singleLine = true
        )

        SectionCard(modifier = Modifier.widthIn(max = 800.dp), title = "Parts (${results.size})") {
            if (results.isEmpty()) EmptyMessage(if (query.isBlank()) "Type to search" else "Nothing matches")

            results.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = item.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (item.description.isNotBlank()) {
                            Text(text = item.description, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Text(
                        modifier = Modifier.width(120.dp),
                        text = when {
                            item.stock == Int.MAX_VALUE -> "Not tracked"
                            item.stock <= 0 -> "Out of stock"
                            else -> "${item.stock} in stock"
                        },
                        fontSize = 13.sp,
                        color = if (item.stock <= 0) MaterialTheme.colorScheme.error else LocalContentColor.current
                    )

                    Text(text = Money.format(item.priceCents), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider()
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}
