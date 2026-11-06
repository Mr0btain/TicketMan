package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.utils.formatBytes
import org.mr0btain.ticketman.logging.LogFileInfo
import org.mr0btain.ticketman.logging.LogLevel
import org.mr0btain.ticketman.logging.LogSettings
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.data.TmLogTerminal
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.selection.TmFilterChip
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.download
import ticketman.composeapp.generated.resources.pause
import ticketman.composeapp.generated.resources.play
import ticketman.composeapp.generated.resources.search
import ticketman.composeapp.generated.resources.trash_2
import kotlin.time.Clock

/**
 * The log, live, in a terminal: this device's, or for admins the server's. Filters by level and text, pauses, and
 * downloads all of it or a range of days or times
 * @param canSeeServer Admins, who can switch to the server's log
 * @param initialSource Whose log shows first
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsDialog(
    logs: LogsHandler,
    canSeeServer: Boolean,
    initialSource: LogSource,
    onDismiss: () -> Unit
) {
    DisposableEffect(Unit) {
        logs.open(initialSource)
        onDispose { logs.close() }
    }

    val source by logs.source.collectAsState()
    val lines by logs.lines.collectAsState()
    val paused by logs.paused.collectAsState()
    val problem by logs.problem.collectAsState()
    val overview by logs.serverOverview.collectAsState()
    val deviceFiles by logs.deviceFiles.collectAsState()
    val deviceSettings by logs.deviceSettings.collectAsState()

    var levels by remember { mutableStateOf(LogLevel.entries.toSet()) }
    var query by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }

    val shown = remember(lines, levels, query) {
        lines.filter { line ->
            line.level in levels &&
                (query.isBlank() || line.message.contains(query, ignoreCase = true) || line.tag.contains(query, ignoreCase = true))
        }
    }

    val summary = when (source) {
        LogSource.DEVICE -> if (logs.savesFiles) filesSummary(deviceFiles, deviceSettings)
            else "This browser doesn't save log files, it keeps what it logged since the page opened"
        LogSource.SERVER -> overview?.let { if (it.storing) filesSummary(it.files, it.settings) else "The server has no folder to save logs in, it keeps them in memory" }
            ?: "Loading the server's log files"
    }

    TmDialog(
        title = "Logs",
        description = summary,
        maxWidth = 1100.dp,
        onDismissRequest = onDismiss,
        actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm, Alignment.End)) {
                TmButton(text = "Close", onClick = onDismiss, variant = TmButtonVariant.GHOST)
                TmButton(text = "Download", onClick = { downloading = true }, leadingIcon = Res.drawable.download)
            }
        }
    ) {
        if (canSeeServer) {
            TmSegmented(
                options = LogSource.entries,
                selected = source,
                onSelect = { logs.open(it) },
                optionLabel = { it.label }
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            LogLevel.entries.forEach { level ->
                TmFilterChip(
                    text = level.chipLabel,
                    selected = level in levels,
                    onClick = { levels = if (level in levels) levels - level else levels + level }
                )
            }
            TmButton(
                text = if (paused) "Resume" else "Pause",
                onClick = logs::togglePaused,
                variant = TmButtonVariant.SECONDARY,
                size = TmControlSize.SMALL,
                leadingIcon = if (paused) Res.drawable.play else Res.drawable.pause
            )
            TmButton(
                text = "Clear",
                onClick = logs::clearView,
                variant = TmButtonVariant.GHOST,
                size = TmControlSize.SMALL,
                leadingIcon = Res.drawable.trash_2
            )
        }

        TmTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Filter by text or tag",
            leadingIcon = Res.drawable.search,
            modifier = Modifier.fillMaxWidth()
        )

        problem?.let { TmNotice(text = "Couldn't read the server's log: $it", kind = TmNoticeKind.WARNING) }

        TmLogTerminal(
            lines = shown,
            title = if (source == LogSource.SERVER) "ticketman — server" else "ticketman — this device",
            status = if (paused) "Paused" else "Live",
            live = !paused,
            highlight = query,
            emptyText = if (lines.isEmpty()) "Waiting for the log" else "No lines match",
            modifier = Modifier.fillMaxWidth().height(440.dp)
        )
    }

    if (downloading) {
        LogDownloadDialog(
            source = source,
            onDownload = { from, to, name, onResult -> logs.download(source, from, to, name, onResult) },
            onDismissRequest = { downloading = false }
        )
    }
}

private val LogLevel.chipLabel: String
    get() = when (this) {
        LogLevel.ERROR -> "Errors"
        LogLevel.WARN -> "Warnings"
        LogLevel.INFO -> "Info"
        LogLevel.DEBUG -> "Debug"
    }

private fun filesSummary(files: List<LogFileInfo>, settings: LogSettings): String {
    val total = files.sumOf { it.sizeBytes }
    val count = if (files.size == 1) "1 file" else "${files.size} files"
    return "$count, ${formatBytes(total)} of ${settings.maxTotalMb} MB. A new file starts each day at ${settings.rolloverText}, the newest ${settings.keepFiles} are kept"
}

/** How much of the log to download */
private enum class LogRange(val label: String) {
    ALL("Everything"),
    TODAY("Today"),
    DAYS("Days"),
    TIMES("Time range")
}

/** A range picked to download: epoch milliseconds (null for no limit) and what the file is called */
private data class PickedRange(val from: Long?, val to: Long?, val name: String)

/**
 * Picks what part of the log to download: all of it, today, whole days, or from one time to another, in this
 * device's time
 * @param onDownload Gets the range and its name for the file, calls back with what went wrong or null when done
 */
@Composable
private fun LogDownloadDialog(
    source: LogSource,
    onDownload: (from: Long?, to: Long?, name: String, onResult: (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.now().toLocalDateTime(zone).date }
    var range by remember { mutableStateOf(LogRange.TODAY) }
    var fromDay by remember { mutableStateOf(today.toString()) }
    var toDay by remember { mutableStateOf(today.toString()) }
    var fromTime by remember { mutableStateOf("$today 08:00") }
    var toTime by remember { mutableStateOf("$today 18:00") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picked: PickedRange? = when (range) {
        LogRange.ALL -> PickedRange(null, null, "all")
        LogRange.TODAY -> PickedRange(today.atStartOfDayIn(zone).toEpochMilliseconds(), null, today.toString())
        LogRange.DAYS -> {
            val start = runCatching { LocalDate.parse(fromDay.trim()) }.getOrNull()
            val end = runCatching { LocalDate.parse(toDay.trim()) }.getOrNull()
            if (start == null || end == null || start > end) null
            else PickedRange(
                from = start.atStartOfDayIn(zone).toEpochMilliseconds(),
                to = end.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1,
                name = if (start == end) "$start" else "$start-to-$end"
            )
        }
        LogRange.TIMES -> {
            val start = parseLocalTime(fromTime)
            val end = parseLocalTime(toTime)
            if (start == null || end == null || start > end) null
            else PickedRange(
                from = start.toInstant(zone).toEpochMilliseconds(),
                to = end.toInstant(zone).toEpochMilliseconds(),
                name = "${fileTime(start)}-to-${fileTime(end)}"
            )
        }
    }

    TmDialog(
        title = if (source == LogSource.SERVER) "Download the server's log" else "Download this device's log",
        description = "Saved as a text file. Times are this device's",
        onDismissRequest = onDismissRequest,
        actions = {
            TmButtonRow(
                confirmText = "Save log",
                confirmEnabled = picked != null,
                loading = saving,
                onCancel = onDismissRequest,
                onConfirm = {
                    val pick = picked ?: return@TmButtonRow
                    saving = true
                    error = null
                    onDownload(pick.from, pick.to, pick.name) { problem ->
                        saving = false
                        error = problem
                        if (problem == null) onDismissRequest()
                    }
                }
            )
        }
    ) {
        TmSegmented(
            label = "What to save",
            options = LogRange.entries,
            selected = range,
            onSelect = { range = it },
            optionLabel = { it.label }
        )

        when (range) {
            LogRange.ALL -> TmNotice(text = "Every line in the saved files", kind = TmNoticeKind.INFO)
            LogRange.TODAY -> TmNotice(text = "From midnight to now", kind = TmNoticeKind.INFO)
            LogRange.DAYS -> {
                TmFieldPair(
                    { TmTextField(modifier = it, value = fromDay, onValueChange = { v -> fromDay = v }, label = "First day", placeholder = "2026-11-01") },
                    { TmTextField(modifier = it, value = toDay, onValueChange = { v -> toDay = v }, label = "Last day", placeholder = "2026-11-06") }
                )
                if (picked == null) TmNotice(text = "Enter both days like 2026-11-01, the first one first", kind = TmNoticeKind.WARNING)
            }
            LogRange.TIMES -> {
                TmFieldPair(
                    { TmTextField(modifier = it, value = fromTime, onValueChange = { v -> fromTime = v }, label = "From", placeholder = "2026-11-06 08:00") },
                    { TmTextField(modifier = it, value = toTime, onValueChange = { v -> toTime = v }, label = "To", placeholder = "2026-11-06 18:00") }
                )
                if (picked == null) TmNotice(text = "Enter both times like 2026-11-06 08:00, the earlier one first", kind = TmNoticeKind.WARNING)
            }
        }

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
    }
}

/** `2026-11-06 08:00` (or with a T, or seconds) as a local date and time */
private fun parseLocalTime(text: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(text.trim().replace(' ', 'T')) }.getOrNull()

private fun fileTime(time: LocalDateTime): String =
    "${time.date}_${time.hour.toString().padStart(2, '0')}${time.minute.toString().padStart(2, '0')}"
