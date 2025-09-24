package org.mr0btain.ticketman.ui.composables.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import org.mr0btain.ticketman.data.search.SearchResult
import java.util.*

/**
 * SearchBox for searching customers. Will return an id or a name and a true if they want to add new if it doesn't exist
 * @param modifier Modifier applied to drop down
 * @param onClick Event called when clicking, uuid passed is identifier for customer
 * @param onQuery The function to run to get the results of a search
 */

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomerSearchBar(
    modifier: Modifier = Modifier,
    onClick: (UUID) -> Unit,
    onQuery: suspend (String) -> List<SearchResult>
) {
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(400)
            .filter { it.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { text ->
                flow {
                    isLoading = true

                    val results = onQuery(text)

                    emit(results)
                }
            }
            .collect { results ->
                suggestions = results

                expanded = results.isNotEmpty()

                isLoading = false
            }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.sizeIn(maxWidth = 400.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search Customer") },
            prefix = {
                /*
                // Get customer if we've matched with one
                val customer = remember(input) { items.fastFirstOrNull { it.identifier.toString() == input } }

                // If a match then prefix the name
                if (customer != null) { Text(text = customer.name.firstName + " " + customer.name.lastName + ": ") }
                 */
            },
            trailingIcon = {
                if (isLoading)
                    CircularProgressIndicator()
                else
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                           },
            colors = OutlinedTextFieldDefaults.colors(),
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 4.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            suggestions.forEach { ranked ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = highlightMatch(ranked.text, query),
                            fontSize = 16.sp
                        )
                    },
                    onClick = {
                        query = ranked.text
                        onClick(ranked.identifier)
                        expanded = false
                    }
                )
            }
        }
    }
}
