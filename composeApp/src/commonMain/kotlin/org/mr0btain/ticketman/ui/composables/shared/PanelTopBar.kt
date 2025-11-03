package org.mr0btain.ticketman.ui.composables.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Label
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.ui.composables.AddOrDeleteButtonRow
import org.mr0btain.ticketman.ui.composables.search.SearchBar
import java.util.UUID

/**
 * Top bar for panels that includes a search bar and an add or delete button
 * @param label The text label to use
 * @param onSearchClick What to do when an item from the search bar is clicked
 * @param onSearchQuery Function to run to search for items
 * @param onAddClick What to do when add is clicked
 * @param onDeleteClick What to do when delete is clicked
 */
@Composable
fun PanelTopBar(
    label: String,
    onSearchClick: (UUID) -> Unit,
    onSearchQuery: suspend (String) -> List<SearchResult>,
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchBar(
            modifier = Modifier.padding(start = 8.dp),
            label = "Search $label",
            onClick =  { onSearchClick(it) },
            onQuery = onSearchQuery
        )

        // Button for adding or deleting an item
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AddOrDeleteButtonRow(
                label = label,
                onAddClick = onAddClick,
                onDeleteClick = onDeleteClick
            )

            Spacer(Modifier.padding(8.dp))
        }
    }
}