package org.mr0btain.ticketman.ui.kit.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.search.TmSearchBar
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.plus
import ticketman.composeapp.generated.resources.trash_2
import kotlin.uuid.Uuid

/** Narrower than this, the buttons only show their icons */
private val COMPACT_WIDTH = 720.dp

/**
 * Toolbar over a list of records: search on the left, delete and add on the right
 * @param label What the records are, singular, like "Customer"
 * @param onSearchClick Called with the record picked from the search results
 * @param onSearchQuery Runs a search
 * @param canDelete Turns the delete button off when nothing is selected
 * @param canChange False for people who can only look: no add or delete, a "View only" badge instead
 * @param extras Goes between the search and the buttons, like a filter
 */
@Composable
fun TmPanelTopBar(
    label: String,
    onSearchClick: (Uuid) -> Unit,
    onSearchQuery: suspend (String) -> List<TmSearchResult>,
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit,
    canDelete: Boolean = true,
    canChange: Boolean = true,
    extras: @Composable RowScope.() -> Unit = {}
) {
    val name = label.lowercase()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Narrow screens get icon buttons so the search box keeps its room
        val compact = maxWidth < COMPACT_WIDTH

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TmSearchBar(
                modifier = if (compact) Modifier.weight(1f) else Modifier.widthIn(min = 240.dp, max = 400.dp),
                placeholder = "Search ${name}s",
                onPick = { onSearchClick(it.identifier) },
                onQuery = onSearchQuery
            )

            if (!compact) {
                extras()
                Spacer(Modifier.weight(1f))
            }

            if (!canChange) {
                TmBadge(text = "View only", tone = TmTheme.colors.neutral)
            } else if (compact) {
                TmIconButton(icon = Res.drawable.trash_2, contentDescription = "Delete $name", onClick = onDeleteClick, enabled = canDelete)
                TmIconButton(icon = Res.drawable.plus, contentDescription = "New $name", onClick = onAddClick, variant = TmButtonVariant.PRIMARY)
            } else {
                TmButton(
                    text = "Delete $name",
                    onClick = onDeleteClick,
                    enabled = canDelete,
                    variant = TmButtonVariant.GHOST,
                    leadingIcon = Res.drawable.trash_2
                )

                TmButton(text = "New $name", onClick = onAddClick, leadingIcon = Res.drawable.plus)
            }
        }
    }
}
