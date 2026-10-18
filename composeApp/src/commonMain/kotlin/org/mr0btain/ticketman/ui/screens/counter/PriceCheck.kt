package org.mr0btain.ticketman.ui.screens.counter

import org.mr0btain.ticketman.ui.kit.containers.TmPageHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmMoneyText
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.package_icon
import ticketman.composeapp.generated.resources.tag

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
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        TmPageHeader(title = "Price check", onBack = onBack)

        TmSearchField(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .focusRequester(focus),
            query = query,
            onQueryChange = {
                query = it
                onSearch(it)
            },
            placeholder = "Part name or description",
            size = TmControlSize.LARGE
        )

        if (results.isEmpty()) {
            TmEmptyState(
                modifier = Modifier.widthIn(max = 640.dp),
                icon = Res.drawable.tag,
                title = if (query.isBlank()) "Type to look up a part" else "No parts match",
                message = if (query.isBlank()) null else "Check the spelling or try part of the name."
            )
            return@Column
        }

        TmPanel(modifier = Modifier.widthIn(max = 800.dp), title = "Parts", subtitle = "${results.size} found", icon = Res.drawable.package_icon) {
            Column {
                results.forEach { item ->
                    TmListRow(
                        title = item.name,
                        subtitle = item.description.ifBlank { null },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                                TmBadge(
                                    text = stockText(item.stock),
                                    tone = if (item.stock <= 0) colors.danger else colors.neutral,
                                    dot = item.stock <= 0
                                )
                                TmMoneyText(item.priceCents, Modifier.widthIn(min = 96.dp), style = TmTheme.typography.numericLarge)
                            }
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}
