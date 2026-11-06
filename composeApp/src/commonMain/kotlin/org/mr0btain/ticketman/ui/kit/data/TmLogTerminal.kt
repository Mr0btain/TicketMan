package org.mr0btain.ticketman.ui.kit.data

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.TimeZone
import org.mr0btain.ticketman.logging.LogFormat
import org.mr0btain.ticketman.logging.LogLevel
import org.mr0btain.ticketman.logging.LogLine
import org.mr0btain.ticketman.ui.kit.foundation.TmVerticalScrollbar
import org.mr0btain.ticketman.ui.kit.foundation.tmShowsScrollbars
import org.mr0btain.ticketman.ui.kit.theme.TmColors
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Log lines drawn like a terminal: monospace on the palette's dark colors in either mode, each line's time, level,
 * tag and message, errors and warnings in their tone, a stack trace under its line. Lines can be selected and copied.
 *
 * Stays on the newest line as lines arrive while it's scrolled to the bottom; scrolling up stops that until it's
 * scrolled back down
 * @param title In the bar across the top, like `server`
 * @param status On the right of the bar, like `Live` or `Paused`, with a dot in [live]'s color
 * @param highlight Text marked wherever it's in a line, a search's
 * @param emptyText Shown when there are no lines
 */
@Composable
fun TmLogTerminal(
    lines: List<LogLine>,
    modifier: Modifier = Modifier,
    title: String = "log",
    status: String? = null,
    live: Boolean = true,
    highlight: String = "",
    emptyText: String = "Nothing logged yet"
) {
    val colors = TmTheme.terminal
    val spacing = TmTheme.spacing
    val shape = TmTheme.shapes.panel
    val style = TmTheme.typography.numeric.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val zone = remember { TimeZone.currentSystemDefault() }
    val listState = rememberLazyListState()

    // Whether the list was at the bottom before the newest lines came in, so it follows them
    var atBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward }.collect { atBottom = it }
    }
    LaunchedEffect(lines.lastOrNull()?.sequence, lines.size) {
        if (atBottom && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.background)
            .border(1.dp, colors.hairline, shape)
    ) {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceSunken)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            listOf(colors.danger.color, colors.warning.color, colors.success.color).forEach { dot ->
                Box(Modifier.size(10.dp).clip(TmTheme.shapes.pill).background(dot.copy(alpha = 0.8f)))
            }
            Spacer(Modifier.size(spacing.xs))
            Text(text = title, style = style, color = colors.textMuted, maxLines = 1, modifier = Modifier.weight(1f))
            status?.let {
                Box(Modifier.size(8.dp).clip(TmTheme.shapes.pill).background(if (live) colors.success.color else colors.textFaint))
                Text(text = it, style = style, color = colors.textMuted, maxLines = 1)
            }
            Text(text = "${lines.size} lines", style = style, color = colors.textFaint, maxLines = 1)
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (lines.isEmpty()) {
                Text(
                    text = "$ $emptyText",
                    style = style,
                    color = colors.textFaint,
                    modifier = Modifier.padding(spacing.md)
                )
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.md,
                            end = if (tmShowsScrollbars) spacing.lg else spacing.md,
                            top = spacing.sm,
                            bottom = spacing.sm
                        )
                    ) {
                        items(lines, key = { it.sequence }) { line ->
                            Text(text = logLineText(line, colors, zone, highlight), style = style, color = colors.text)
                        }
                    }
                }
                TmVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

/** The tone a level is drawn in */
private fun levelColor(level: LogLevel, colors: TmColors): Color = when (level) {
    LogLevel.ERROR -> colors.danger.color
    LogLevel.WARN -> colors.warning.color
    LogLevel.INFO -> colors.success.color
    LogLevel.DEBUG -> colors.textFaint
}

private fun logLineText(line: LogLine, colors: TmColors, zone: TimeZone, highlight: String): AnnotatedString = buildAnnotatedString {
    // Only the time of day, the date is in the file's name
    withStyle(SpanStyle(color = colors.textFaint)) { append(LogFormat.formatTime(line.time, zone).substring(11)) }
    append(' ')
    withStyle(SpanStyle(color = levelColor(line.level, colors), fontWeight = FontWeight.SemiBold)) { append(line.level.label.padEnd(5)) }
    append(' ')
    withStyle(SpanStyle(color = colors.accent.color)) { append("[${line.tag}]") }
    append(' ')

    val messageColor = when (line.level) {
        LogLevel.ERROR -> colors.danger.color
        LogLevel.DEBUG -> colors.textMuted
        else -> colors.text
    }
    val message = line.message
    if (highlight.isBlank()) {
        withStyle(SpanStyle(color = messageColor)) { append(message) }
        return@buildAnnotatedString
    }

    // Each match marked like a terminal's search
    val lower = message.lowercase()
    val query = highlight.lowercase()
    var start = 0
    while (true) {
        val index = lower.indexOf(query, start)
        if (index == -1) {
            withStyle(SpanStyle(color = messageColor)) { append(message.substring(start)) }
            break
        }
        withStyle(SpanStyle(color = messageColor)) { append(message.substring(start, index)) }
        withStyle(SpanStyle(color = colors.warning.onColor, background = colors.warning.color)) {
            append(message.substring(index, index + query.length))
        }
        start = index + query.length
    }
}
