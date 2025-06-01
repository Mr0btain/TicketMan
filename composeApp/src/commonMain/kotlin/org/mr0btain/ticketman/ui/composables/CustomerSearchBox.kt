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
import org.mr0btain.ticketman.components.data.components.CustomerInfo
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.search

/**
 * SearchBox for searching customers. Will return an id or a name and a true if they want to add new if it doesn't exist
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSearchBox(
    modifier: Modifier = Modifier,
    items: List<CustomerInfo>,
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
                    (it.name.firstName + it.name.lastName).lowercase()
                        .contains(query.lowercase().replace(" ", ""))
                    || it.identifier.contains(query)
                }.sortedBy { it.name.firstName + it.name.lastName + it.identifier }
            )
        } else {
            // Clear list
            filtered.clear()

            // Add all sorted
            filtered.addAll(items.sortedBy { it.name.firstName + it.name.lastName + it.identifier })
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
                    label = "Search Customers",
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
                                    isExpanded = false
                                    onResult(query, true)

                                    focusManager.clearFocus()
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
                                    query = item.name.firstName + " " + item.name.lastName + ": " + item.identifier
                                    shouldShowPlus = false
                                    isExpanded = false
                                    onResult(item.identifier, false)
                                }
                            ),
                            headlineContent = {
                                Text(
                                    text = item.name.firstName + " " + item.name.lastName + ": " + item.identifier,
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