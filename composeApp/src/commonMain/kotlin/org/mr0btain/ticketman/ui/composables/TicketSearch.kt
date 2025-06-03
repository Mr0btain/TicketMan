package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.components.TicketInfo
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.search

/**
 * SearchBox for searching tickets. Will return an id or a name and a true if they want to add new if it doesn't exist
 * @param modifier Modifier applied to drop down
 * @param items List of all tickets
 * @param input The state of the text field
 * @param onInputChange Event called when typing
 * @param onClick Event called when clicking, string passed is identifier for ticket
 */

// TODO: Possibly add come extra into prefix like customer name?

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketSearch(
    modifier: Modifier = Modifier,
    items: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredList = remember(input) { items.filter { it.contains(input) } }
    val focusManager = LocalFocusManager.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.sizeIn(maxWidth = 300.dp)
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                // Check if were expanded on typing, because we should be
                if (!expanded) expanded = true

                // Call on input change
                onInputChange(it)
                            },
            label = { Text("Search Tickets") },
            prefix = {
                // Get matching ticket if there is one
                val ticket = remember(input) { items.filter { it == input } }

                // Show prefix
                if (ticket.isNotEmpty()) { Text("Ticket:") }
                     },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(),
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
            filteredList.forEach { option ->
                DropdownMenuItem(
                    text = { Text(
                        text = "Ticket: $option",
                        fontSize = 16.sp
                    ) },
                    onClick = {
                        // minimize the drop-down
                        expanded = false

                        // Clear focus
                        focusManager.clearFocus()

                        // Call on click
                        onClick(option)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketSearchBoxOld(
    modifier: Modifier = Modifier,
    items: List<TicketInfo>,
    onResult: (String, Boolean) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = rememberSaveable { items.toMutableStateList() }
    var shouldShowPlus by rememberSaveable { mutableStateOf(true) }

    val focusManager = LocalFocusManager.current

    // Launched effect to filter search results. First we check if query is empty or not, if it is
    // then we add all the items sorted to the list. Else we filter them
    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            // Clear current
            filtered.clear()

            // Filter items and add it to filtered
            filtered.addAll(
                items.filter {
                    (it.identifier + it.customerId).contains(query)
                }.sortedBy { it.identifier + it.customerId }
            )
        } else {
            // Clear list
            filtered.clear()

            // Add all sorted
            filtered.addAll(items.sortedBy { it.identifier + it.customerId })
        }
    }

    // The actual search bar
    Box(modifier.sizeIn(maxHeight = 300.dp)
    ) {
        SearchBar(
            modifier = Modifier,
            inputField = {
                // This is the text field
                CustomInputField(
                    query = query,
                    onQueryChange = { item ->
                        query = item
                        if (!shouldShowPlus) shouldShowPlus = true
                    },
                    onSearch = { item ->
                        onResult(item, true)
                        isExpanded = false
                    },
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = it },
                    label = "Search Tickets",
                    leadingIcon = {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(Res.drawable.search),
                            contentDescription = ""
                        )
                    },
                    trailingIcon = {
                        // For adding new
                        if (query.isNotEmpty() && shouldShowPlus) {
                            IconButton(
                                modifier = Modifier.size(26.dp),
                                onClick = {
                                    focusManager.clearFocus()
                                    onResult(query, true)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.plus),
                                    contentDescription = ""
                                )
                            }
                        }
                    }
                )
            },
            shape = RoundedCornerShape(6.dp),
            expanded = isExpanded,
            onExpandedChange = { isExpanded = it },
            colors = SearchBarDefaults.colors(
                containerColor = Color.Transparent
            )
        ) {
            // This is the list
            Surface(
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp
            ) {
                LazyColumn {
                    items(filtered) { item ->
                        ListItem(
                            modifier = Modifier.clickable(
                                onClick = {
                                    query = item.identifier + ": "  + item.customerId
                                    shouldShowPlus = false
                                    isExpanded = false
                                    focusManager.clearFocus()
                                    onResult(item.identifier, false)
                                }
                            ),
                            headlineContent = {
                                Text(
                                    text = "Ticket: " + item.identifier+ " Customer: " + item.customerId,
                                    fontSize = 16.sp
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}