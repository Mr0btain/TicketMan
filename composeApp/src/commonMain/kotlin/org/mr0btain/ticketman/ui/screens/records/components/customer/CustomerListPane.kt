package org.mr0btain.ticketman.ui.screens.records.components.customer

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.records.components.NARROW_LIST_WIDTH
import org.mr0btain.ticketman.ui.screens.records.components.RecordNameCell
import org.mr0btain.ticketman.ui.screens.records.components.RecordsList
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.users
import kotlin.uuid.Uuid

private val COLUMNS = listOf(
    TmColumn("Name", weight = 1.2f),
    TmColumn("Phone", weight = 0.9f),
    TmColumn("Email", weight = 1.2f),
    TmColumn("Street", weight = 1f)
)

// Narrow: the name, with the phone or email under it
private val NARROW_COLUMNS = listOf(TmColumn("Customer"))

@Composable
fun CustomerListPane(
    customerList: List<Customer>,
    onClick: (Uuid) -> Unit,
    selectedId: Uuid,
    lazyListState: LazyListState,
    loadError: String? = null
) {
    val colors = TmTheme.colors

    BoxWithConstraints {
        val narrow = maxWidth < NARROW_LIST_WIDTH
        val columns = if (narrow) NARROW_COLUMNS else COLUMNS

        RecordsList(
            columns = columns,
            state = lazyListState,
            isEmpty = customerList.isEmpty(),
            emptyTitle = "No customers yet",
            emptyIcon = Res.drawable.users,
            emptyMessage = "Add one with New customer, or check you're connected.",
            loadError = loadError
        ) {
            items(customerList, key = { it.identifier }) { customer ->
                TmTableRow(columns, selected = customer.identifier == selectedId, onClick = { onClick(customer.identifier) }) { column ->
                    if (narrow) {
                        RecordNameCell(customer.name, listOf(customer.phone, customer.email).filter { it.isNotBlank() }.joinToString(" · "))
                        return@TmTableRow
                    }

                    val (text, primary) = when (column) {
                        0 -> customer.name to true
                        1 -> customer.phone to false
                        2 -> customer.email to false
                        else -> customer.address.street to false
                    }

                    Text(
                        text = text,
                        style = if (primary) TmTheme.typography.label else TmTheme.typography.body,
                        color = if (primary) colors.text else colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
