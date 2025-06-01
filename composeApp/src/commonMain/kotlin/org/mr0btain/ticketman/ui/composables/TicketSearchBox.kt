package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketSearchBox(
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