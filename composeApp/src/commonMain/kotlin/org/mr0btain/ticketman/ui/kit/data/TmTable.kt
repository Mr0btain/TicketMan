package org.mr0btain.ticketman.ui.kit.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * A table column. Give it a fixed [width], or leave that null to share the leftover space by [weight]
 * @param end Lines the column up on the right, for numbers and buttons
 */
@Immutable
data class TmColumn(
    val title: String,
    val width: Dp? = null,
    val weight: Float = 1f,
    val end: Boolean = false
)

/** Column titles over a table */
@Composable
fun TmTableHeader(columns: List<TmColumn>, modifier: Modifier = Modifier) {
    TableLine(columns, modifier.heightIn(min = 32.dp)) { index ->
        Text(
            text = columns[index].title,
            style = TmTheme.typography.caption,
            color = TmTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    TmDivider()
}

/**
 * One row of a table, [cell] draws the column at that index
 * @param onClick Makes the row clickable, like opening the item
 * @param selected Highlights the row, for the item open beside the table
 */
@Composable
fun TmTableRow(
    columns: List<TmColumn>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    cell: @Composable (Int) -> Unit
) {
    val shape = TmTheme.shapes.control

    TableLine(
        columns = columns,
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(if (selected) TmTheme.colors.accent.soft else Color.Transparent, shape)
            .then(if (onClick != null) Modifier.tmClickable(onClick = onClick, shape = shape) else Modifier),
        cell = cell
    )
}

@Composable
private fun TableLine(columns: List<TmColumn>, modifier: Modifier, cell: @Composable (Int) -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TmTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.forEachIndexed { index, column ->
            Box(
                modifier = columnModifier(column),
                contentAlignment = if (column.end) Alignment.CenterEnd else Alignment.CenterStart
            ) { cell(index) }
        }
    }
}

@Composable
private fun RowScope.columnModifier(column: TmColumn): Modifier =
    if (column.width != null) Modifier.width(column.width) else Modifier.weight(column.weight)
