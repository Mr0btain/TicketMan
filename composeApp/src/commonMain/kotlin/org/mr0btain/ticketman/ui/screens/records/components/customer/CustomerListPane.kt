package org.mr0btain.ticketman.ui.screens.records.components.customer

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
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

@Composable
fun CustomerListPane(
    customerList: List<Customer>,
    onClick: (Uuid) -> Unit,
    selectedId: Uuid,
    lazyListState: LazyListState
) {
    val colors = TmTheme.colors

    RecordsList(
        columns = COLUMNS,
        state = lazyListState,
        isEmpty = customerList.isEmpty(),
        emptyTitle = "No customers yet",
        emptyIcon = Res.drawable.users,
        emptyMessage = "Add one with New customer, or check you're connected."
    ) {
        items(customerList, key = { it.identifier }) { customer ->
            TmTableRow(COLUMNS, selected = customer.identifier == selectedId, onClick = { onClick(customer.identifier) }) { column ->
                val (text, primary) = when (column) {
                    0 -> customer.cleanName() to true
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
