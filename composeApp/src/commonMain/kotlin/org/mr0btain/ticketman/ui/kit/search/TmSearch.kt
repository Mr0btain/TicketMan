package org.mr0btain.ticketman.ui.kit.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmMenu
import org.mr0btain.ticketman.ui.kit.selection.TmMenuItem
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.search
import ticketman.composeapp.generated.resources.x
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** How long typing has to pause before a search runs */
val TM_SEARCH_DEBOUNCE: Duration = 350.milliseconds

/**
 * Text field with a search icon and a clear button
 * @param onSearch Called when enter is pressed
 * @param loading Shows a spinner in place of the clear button
 */
@Composable
fun TmSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    label: String? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: TmControlSize = TmControlSize.MEDIUM,
    onSearch: (String) -> Unit = {}
) {
    TmTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        enabled = enabled,
        size = size,
        leadingIcon = Res.drawable.search,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        trailing = {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(size.iconSize),
                    color = TmTheme.colors.accent.color,
                    strokeWidth = 2.dp
                )

                query.isNotEmpty() -> TmIconButton(
                    icon = Res.drawable.x,
                    contentDescription = "Clear search",
                    onClick = { onQueryChange("") },
                    size = TmControlSize.SMALL
                )
            }
        }
    )
}

/**
 * Search field that looks things up as you type and lists matches under it
 * @param onQuery Runs the search, called once typing pauses for [TM_SEARCH_DEBOUNCE]
 * @param onPick Called with the match that was clicked, the field then shows its text
 * @param emptyText Shown in the list when a search finds nothing, null hides the list instead
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun TmSearchBar(
    onQuery: suspend (String) -> List<SearchResult>,
    onPick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    placeholder: String = "Search",
    label: String? = null,
    enabled: Boolean = true,
    size: TmControlSize = TmControlSize.MEDIUM,
    emptyText: String? = "No matches",
    debounce: Duration = TM_SEARCH_DEBOUNCE
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    // Picking a match fills in its text, which shouldn't search again
    var picked by remember { mutableStateOf(initialQuery.isNotBlank()) }
    val search by rememberUpdatedState(onQuery)

    LaunchedEffect(debounce) {
        snapshotFlow { query }
            .debounce(debounce)
            .distinctUntilChanged()
            .collectLatest { text ->
                if (text.isBlank() || picked) {
                    results = emptyList()
                    expanded = false
                    return@collectLatest
                }

                loading = true
                results = search(text)
                loading = false
                expanded = results.isNotEmpty() || emptyText != null
            }
    }

    Box(modifier) {
        TmSearchField(
            query = query,
            onQueryChange = {
                picked = false
                query = it
            },
            placeholder = placeholder,
            label = label,
            enabled = enabled,
            loading = loading,
            size = size
        )

        TmMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            focusable = false
        ) {
            if (results.isEmpty() && emptyText != null) {
                Text(
                    modifier = Modifier.padding(horizontal = TmTheme.spacing.md, vertical = TmTheme.spacing.sm),
                    text = emptyText,
                    style = TmTheme.typography.body,
                    color = TmTheme.colors.textMuted
                )
            }

            results.forEach { result ->
                TmMenuItem(
                    text = highlightMatch(result.text, query, TmTheme.colors.accent.color),
                    onClick = {
                        picked = true
                        query = result.text
                        expanded = false
                        onPick(result)
                    }
                )
            }
        }
    }
}
