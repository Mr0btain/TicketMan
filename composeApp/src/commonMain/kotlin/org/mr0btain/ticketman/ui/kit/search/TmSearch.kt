package org.mr0btain.ticketman.ui.kit.search

import org.mr0btain.uikit.search.TM_SEARCH_DEBOUNCE
import org.mr0btain.uikit.search.TmSearchField
import org.mr0btain.uikit.search.highlightMatch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.uikit.foundation.TmControlSize
import org.mr0btain.uikit.selection.TmMenu
import org.mr0btain.uikit.selection.TmMenuItem
import org.mr0btain.uikit.theme.TmTheme
import ticketman.composeapp.generated.resources.search
import kotlin.time.Duration

/**
 * Search field that looks things up as you type and lists matches under it
 * @param onQuery Runs the search, called once typing pauses for [TM_SEARCH_DEBOUNCE]
 * @param onPick Called with the match that was clicked, the field then shows its text
 * @param emptyText Shown in the list when a search finds nothing, null hides the list instead
 */
@OptIn(FlowPreview::class)
@Composable
fun TmSearchBar(
    onQuery: suspend (String) -> List<TmSearchResult>,
    onPick: (TmSearchResult) -> Unit,
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
    var results by remember { mutableStateOf<List<TmSearchResult>>(emptyList()) }
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
