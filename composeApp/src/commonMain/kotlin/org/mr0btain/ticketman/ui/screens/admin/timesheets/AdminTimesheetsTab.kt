package org.mr0btain.ticketman.ui.screens.admin.timesheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.mr0btain.ticketman.components.utils.formatDay
import org.mr0btain.ticketman.components.utils.formatHours
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.timeclock.TimeEntry
import org.mr0btain.ticketman.data.timeclock.Timesheet
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.containers.TmConfirmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.containers.TmPanel
import org.mr0btain.ticketman.ui.kit.data.TmColumn
import org.mr0btain.ticketman.ui.kit.data.TmTableHeader
import org.mr0btain.ticketman.ui.kit.data.TmTableRow
import org.mr0btain.ticketman.ui.kit.feedback.TmBadge
import org.mr0btain.ticketman.ui.kit.feedback.TmEmptyState
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.admin.invoices.ExportRange
import org.mr0btain.ticketman.ui.screens.admin.reports.CustomDays
import ticketman.composeapp.generated.resources.*

/**
 * Everyone's time on the clock over some days, who's on now, and fixing times by hand: adding a forgotten stretch,
 * changing one, or deleting it
 * @param onSave Saves an entry, new when the flag is set, calling back with what went wrong or null once saved
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminTimesheetsTab(
    sheet: Timesheet?,
    onNow: List<TimeEntry>,
    users: List<User>,
    range: ExportRange,
    days: Pair<LocalDate, LocalDate>?,
    onPick: (ExportRange) -> Unit,
    onShowDays: (LocalDate, LocalDate) -> Unit,
    onSave: (TimeEntry, Boolean, (String?) -> Unit) -> Unit,
    onRemove: (TimeEntry) -> Unit
) {
    val spacing = TmTheme.spacing
    val colors = TmTheme.colors
    var editing by remember { mutableStateOf<Pair<TimeEntry?, Boolean>?>(null) }
    var deleting by remember { mutableStateOf<TimeEntry?>(null) }

    editing?.let { (entry, isNew) ->
        TimeEntryDialog(
            entry = entry,
            users = users,
            onDelete = entry?.let { { editing = null; deleting = it } },
            onSave = { saved, onResult -> onSave(saved, isNew) { problem -> onResult(problem); if (problem == null) editing = null } },
            onDismissRequest = { editing = null }
        )
    }

    deleting?.let { entry ->
        TmConfirmDialog(
            title = "Delete this time?",
            message = "${entry.userName}'s ${formatDay(entry.clockedInAt)}, ${formatTime(entry.clockedInAt)} to ${entry.clockedOutAt?.let { formatTime(it) } ?: "now"}, goes for good.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                onRemove(entry)
                deleting = null
            },
            onDismissRequest = { deleting = null }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            itemVerticalAlignment = Alignment.Bottom
        ) {
            TmSegmented(options = ExportRange.entries, selected = range, onSelect = onPick, optionLabel = { it.label })
            if (range == ExportRange.CUSTOM) CustomDays(days, onShowDays)
            TmButton(text = "Add time", leadingIcon = Res.drawable.plus, variant = TmButtonVariant.SECONDARY, onClick = { editing = null to true })
        }

        TmPanel(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(), title = "On the clock now", icon = Res.drawable.clock) {
            if (onNow.isEmpty()) Text(text = "Nobody", style = TmTheme.typography.body, color = colors.textMuted)
            onNow.forEach { entry ->
                TmListRow(
                    title = entry.userName,
                    subtitle = "Since ${formatDay(entry.clockedInAt)} ${formatTime(entry.clockedInAt)}" + entry.station.takeIf { it.isNotBlank() }?.let { " at $it" }.orEmpty(),
                    icon = Res.drawable.user,
                    trailing = { TmIconButton(icon = Res.drawable.edit_2, contentDescription = "Change", onClick = { editing = entry to false }) }
                )
            }
        }

        if (sheet == null) {
            TmEmptyState(
                icon = Res.drawable.clock,
                title = if (range == ExportRange.CUSTOM && days == null) "Pick the days" else "No timesheets yet",
                message = if (range == ExportRange.CUSTOM && days == null) "Enter the first and last day, then show them." else "Make sure you're connected and logged in as an admin, then refresh."
            )
            return@Column
        }

        Text(text = "${sheet.from} to ${sheet.to}", style = TmTheme.typography.title, color = colors.text)
        if (sheet.people.isEmpty()) Text(text = "Nobody was on the clock", style = TmTheme.typography.body, color = colors.textMuted)

        val wideColumns = listOf(
            TmColumn("Day", width = 120.dp),
            TmColumn("In", width = 64.dp),
            TmColumn("Out", width = 64.dp),
            TmColumn("Hours", width = 72.dp, end = true),
            TmColumn("Where", weight = 1f),
            TmColumn("", width = 88.dp, end = true)
        )
        // Narrow: where and the note go under the day, and tapping the row changes or deletes it
        val narrowColumns = listOf(
            TmColumn("Day", weight = 1f),
            TmColumn("In", width = 52.dp),
            TmColumn("Out", width = 52.dp),
            TmColumn("Hours", width = 60.dp, end = true)
        )

        sheet.people.forEach { person ->
            TmPanel(
                modifier = Modifier.widthIn(max = 880.dp).fillMaxWidth(),
                title = person.name,
                subtitle = if (person.entries.size == 1) "Once on the clock" else "${person.entries.size} times on the clock",
                icon = Res.drawable.user,
                actions = { Text(text = formatHours(person.hours), style = TmTheme.typography.numericLarge, color = colors.text) }
            ) {
                BoxWithConstraints {
                    val narrow = maxWidth < ENTRY_TABLE_WIDTH
                    val columns = if (narrow) narrowColumns else wideColumns

                    Column {
                        TmTableHeader(columns)
                        person.entries.forEach { entry ->
                            val where = listOf(entry.station, entry.note, entry.editedBy.takeIf { it.isNotBlank() }?.let { "changed by $it" }.orEmpty())
                                .filter { it.isNotBlank() }.joinToString(", ")

                            TmTableRow(columns, onClick = if (narrow) ({ editing = entry to false }) else null) { index ->
                                when (index) {
                                    0 -> Column {
                                        Text(text = formatDay(entry.clockedInAt), style = TmTheme.typography.body, color = colors.text)
                                        if (narrow && where.isNotBlank()) Text(text = where, style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 1)
                                    }
                                    1 -> Text(text = formatTime(entry.clockedInAt), style = TmTheme.typography.numeric, color = colors.text)
                                    2 -> entry.clockedOutAt?.let { Text(text = formatTime(it), style = TmTheme.typography.numeric, color = colors.text) }
                                        ?: TmBadge(text = "On", tone = colors.success)
                                    3 -> Text(text = formatHours(entry.hours(sheet.generatedAt)), style = TmTheme.typography.numeric, color = colors.text)
                                    4 -> Text(text = where, style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 2)
                                    else -> Row {
                                        TmIconButton(icon = Res.drawable.edit_2, contentDescription = "Change", onClick = { editing = entry to false })
                                        TmIconButton(icon = Res.drawable.trash_2, contentDescription = "Delete", onClick = { deleting = entry })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Narrower than this, a stretch's place and note go under its day and its buttons are in the dialog */
private val ENTRY_TABLE_WIDTH = 620.dp
