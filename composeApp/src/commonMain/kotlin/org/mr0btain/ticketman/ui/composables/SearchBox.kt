package org.mr0btain.ticketman.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.search
import ticketman.composeapp.generated.resources.x

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBox(
    modifier: Modifier = Modifier,
    label: String,
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<String>,
    onSearch: (String) -> Unit,
    onResultClick: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Box(modifier.sizeIn(maxHeight = 300.dp)
    ) {
        SearchBar(
            modifier = Modifier,
            inputField = {
                CustomInputField(
                    query = query,
                    onQueryChange = { item -> onQueryChange(item) },
                    onSearch = { item -> onSearch(item) },
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    label = label,
                    leadingIcon = {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(Res.drawable.search),
                            contentDescription = ""
                        )
                    },
                    trailingIcon = trailingIcon
                )
            },
            shape = RoundedCornerShape(6.dp),
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            colors = SearchBarDefaults.colors(
                containerColor = Color.Transparent
            )
        ) {
            // Show search results in a lazy column for better performance
            Surface(
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp
            ) {
                LazyColumn {
                    items(searchResults) { item ->
                        ListItem(
                            modifier = Modifier.clickable(
                                onClick = { onResultClick(item) }
                            ),
                            headlineContent = {
                                Text(
                                    text = item,
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

/**
 * Custom input field for SearchBar using outline text field instead
 */

@ExperimentalMaterial3Api
@Composable
fun CustomInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    label: String = ""
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsState().value
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val shouldClearFocus = !expanded && focused

    // If we lose focus call close on the list
    LaunchedEffect(focused) { if (!focused) { onExpandedChange(false) } }

    // Check if we should clear focus
    LaunchedEffect(expanded) { if (shouldClearFocus) { focusManager.clearFocus() } }

    OutlinedTextField(
        modifier =
            modifier
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onExpandedChange(true) },
        value = query,
        onValueChange = onQueryChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        textStyle = LocalTextStyle.current,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch(query)
            }
        ),
        interactionSource = interactionSource,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon
    )
}