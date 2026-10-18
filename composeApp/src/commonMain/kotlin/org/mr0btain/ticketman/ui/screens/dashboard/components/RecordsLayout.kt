package org.mr0btain.ticketman.ui.screens.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmLazyColumn
import org.mr0btain.ticketman.ui.kit.containers.TmPaneHeader
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.edit_2

/** Narrower than this, the list and details stack instead of sitting side by side */
private val SIDE_BY_SIDE_WIDTH = 760.dp

/**
 * The layout every records tab shares: a toolbar, the list, and the picked record's details beside it.
 * On narrow screens the details go under the list
 */
@Composable
fun RecordsLayout(
    toolbar: @Composable () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    val spacing = TmTheme.spacing

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        toolbar()

        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= SIDE_BY_SIDE_WIDTH) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TmSurface(Modifier.weight(0.56f).fillMaxHeight()) { list() }
                    TmSurface(Modifier.weight(0.44f).fillMaxHeight()) { detail() }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TmSurface(Modifier.weight(0.5f).fillMaxWidth()) { list() }
                    TmSurface(Modifier.weight(0.5f).fillMaxWidth()) { detail() }
                }
            }
        }
    }
}

/**
 * A table of records that loads more as it scrolls
 * @param emptyTitle Shown when there's nothing in the list
 */
@Composable
fun RecordsList(
    columns: List<TmColumn>,
    state: LazyListState,
    isEmpty: Boolean,
    emptyTitle: String,
    emptyIcon: DrawableResource,
    emptyMessage: String? = null,
    content: LazyListScope.() -> Unit
) {
    Column(Modifier.padding(TmTheme.spacing.sm)) {
        TmTableHeader(columns)

        if (isEmpty) {
            TmEmptyState(icon = emptyIcon, title = emptyTitle, message = emptyMessage)
        } else {
            TmLazyColumn(modifier = Modifier.fillMaxSize(), state = state, content = content)
        }
    }
}

/**
 * Details of the picked record: a title row with an edit button, then [content] scrolling under it
 * @param emptyTitle Shown in place of the details when nothing is picked
 */
@Composable
fun RecordDetail(
    title: String,
    subtitle: String?,
    icon: DrawableResource,
    isEmpty: Boolean,
    emptyTitle: String,
    onEdit: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (isEmpty) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TmEmptyState(icon = icon, title = emptyTitle, message = "Pick one from the list or search for it.")
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        TmPaneHeader(
            title = title,
            subtitle = subtitle,
            icon = icon,
            actions = { TmButton(text = "Edit", onClick = onEdit, variant = TmButtonVariant.SECONDARY, leadingIcon = Res.drawable.edit_2) }
        )

        TmDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(TmTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.lg),
            content = content
        )
    }
}

/** A titled group inside [RecordDetail] */
@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
        Text(text = title, style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
        content()
    }
}
