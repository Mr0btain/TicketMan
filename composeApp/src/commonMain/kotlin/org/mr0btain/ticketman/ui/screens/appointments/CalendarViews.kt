package org.mr0btain.ticketman.ui.screens.appointments

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.schedule.ScheduleEntry
import org.mr0btain.ticketman.data.schedule.ScheduleRules
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.ui.kit.containers.TmSectionLabel
import org.mr0btain.ticketman.ui.kit.data.TmScheduleBlock
import org.mr0btain.ticketman.ui.kit.feedback.tone
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.appointments.components.AppointmentsHandler
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val zone: TimeZone get() = TimeZone.currentSystemDefault()

/** Height of one row of blocks on the staff board and in a month's week */
private val LANE = 44.dp
private val MONTH_LANE = 26.dp

/**
 * A piece of something on a run of days in a row: its first column, how many columns it covers, and whether it
 * carries on before or after the row
 */
internal data class Span<T>(val item: T, val start: Int, val length: Int, val before: Boolean, val after: Boolean) {
    val end: Int get() = start + length
}

/** Where each entry falls on [days], which run one after another. Ones not on them are left out */
internal fun spansOf(entries: List<ScheduleEntry>, days: List<LocalDate>): List<Span<ScheduleEntry>> {
    if (days.isEmpty()) return emptyList()
    return entries.mapNotNull { entry ->
        val on = entry.days(zone)
        val first = days.indexOfFirst { it in on }.takeIf { it >= 0 } ?: return@mapNotNull null
        val last = days.indexOfLast { it in on }
        Span(entry, first, last - first + 1, before = on.first() < days.first(), after = on.last() > days.last())
    }
}

/** Spans put in rows that don't overlap, first come first: the longest at each start goes highest */
internal fun <T> lanes(spans: List<Span<T>>): List<List<Span<T>>> {
    val lanes = mutableListOf<MutableList<Span<T>>>()
    spans.sortedWith(compareBy<Span<T>> { it.start }.thenByDescending { it.length }).forEach { span ->
        val lane = lanes.firstOrNull { lane -> lane.last().end <= span.start }
        if (lane != null) lane += span else lanes += mutableListOf(span)
    }
    return lanes
}

/** One lane of spans across [columns] equal columns, with gaps where nothing is */
@Composable
private fun <T> LaneRow(lane: List<Span<T>>, columns: Int, height: Dp, block: @Composable (Span<T>, Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().height(height).padding(vertical = 2.dp)) {
        var at = 0
        lane.forEach { span ->
            if (span.start > at) Spacer(Modifier.weight((span.start - at).toFloat()))
            block(span, Modifier.weight(span.length.toFloat()).fillMaxHeight().padding(horizontal = 2.dp))
            at = span.end
        }
        if (columns > at) Spacer(Modifier.weight((columns - at).toFloat()))
    }
}

private fun days(from: LocalDate, count: Int): List<LocalDate> = (0 until count).map { from.plus(DatePeriod(days = it)) }

private fun dayOf(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date

/** "9:00 to 17:00, 8h", or "All day" and its days for time off */
internal fun ScheduleEntry.hoursLabel(): String =
    if (allDay) days(zone).size.let { if (it == 1) "All day" else "$it days" }
    else "${formatTime(startsAt)} to ${formatTime(endsAt)}, ${ScheduleRules.hoursText(hours)}"

/** "09:00–17:00", or its days for a whole day one, for narrow places like the staff board */
internal fun ScheduleEntry.shortLabel(): String =
    if (allDay) days(zone).size.let { if (it == 1) "All day" else "$it days" } else "${formatTime(startsAt)}–${formatTime(endsAt)}"

/** The person's name, or the kind for an entry with no name filled in */
private fun ScheduleEntry.who(): String = userName.ifBlank { kind.label }

private fun weekdayLetters(day: LocalDate): String = day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }

// ------------------------------------------ Day ------------------------------------------

/**
 * One day: who's working down a timeline of the day's hours, each shift a bar from its start to its end, and the
 * day's appointments under it
 * @param onEntry Opens an entry, null when the schedule can't be changed here
 */
@Composable
internal fun CalendarDay(
    day: LocalDate,
    appointments: List<Appointment>,
    schedule: List<ScheduleEntry>,
    onEntry: ((ScheduleEntry) -> Unit)?,
    appointmentList: @Composable () -> Unit
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    val today = schedule.filter { it.isOn(day, zone) }

    // The working day, stretched to fit shifts outside it
    val timed = today.filter { !it.allDay }
    val firstHour = (timed.minOfOrNull { if (dayOf(it.startsAt) < day) 0 else Instant.fromEpochMilliseconds(it.startsAt).toLocalDateTime(zone).hour } ?: 8).coerceAtMost(8)
    val lastHour = (timed.maxOfOrNull { if (dayOf(it.endsAt - 1) > day) 24 else Instant.fromEpochMilliseconds(it.endsAt - 1).toLocalDateTime(zone).hour + 1 } ?: 18).coerceAtLeast(18)
    val hours = lastHour - firstHour

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        TmSectionLabel(text = "Working" + if (today.isEmpty()) ", nobody on the schedule" else "")

        if (today.isNotEmpty()) {
            Column(Modifier.widthIn(max = 1100.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                // The hour marks
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(NAME_WIDTH))
                    Row(Modifier.weight(1f)) {
                        (firstHour until lastHour).forEach { hour ->
                            Text(
                                modifier = Modifier.weight(1f),
                                text = if ((hour - firstHour) % 2 == 0) "${hour % 24}:00" else "",
                                style = TmTheme.typography.caption,
                                color = colors.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }

                today.sortedWith(compareBy<ScheduleEntry> { it.allDay }.thenBy { it.startsAt }).forEach { entry ->
                    Row(Modifier.fillMaxWidth().height(LANE), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.width(NAME_WIDTH).padding(end = spacing.sm),
                            text = entry.who(),
                            style = TmTheme.typography.label,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Where it falls in the day's hours, a whole day one across all of them
                        val dayStart = kotlinx.datetime.LocalDateTime(day, kotlinx.datetime.LocalTime(0, 0))
                        val startHour = if (entry.allDay || dayOf(entry.startsAt) < day) firstHour.toDouble()
                            else Instant.fromEpochMilliseconds(entry.startsAt).toLocalDateTime(zone).let { it.hour + it.minute / 60.0 }
                        val endHour = if (entry.allDay || dayOf(entry.endsAt - 1) > day) lastHour.toDouble()
                            else Instant.fromEpochMilliseconds(entry.endsAt).toLocalDateTime(zone).let { (if (it.date > dayStart.date) 24 else it.hour) + it.minute / 60.0 }
                        val before = (startHour - firstHour).coerceIn(0.0, hours.toDouble())
                        val length = (endHour - startHour).coerceIn(0.1, hours - before)
                        Row(Modifier.weight(1f).fillMaxHeight().padding(vertical = 2.dp)) {
                            if (before > 0) Spacer(Modifier.weight(before.toFloat()))
                            TmScheduleBlock(
                                modifier = Modifier.weight(length.toFloat()).fillMaxHeight(),
                                title = entry.kind.label + entry.note.ifBlank { null }?.let { ", $it" }.orEmpty(),
                                subtitle = entry.hoursLabel(),
                                tone = colors.tone(entry.kind.tone),
                                onClick = onEntry?.let { { it(entry) } }
                            )
                            val after = hours - before - length
                            if (after > 0.01) Spacer(Modifier.weight(after.toFloat()))
                        }
                    }
                }
            }
        }

        TmSectionLabel(text = if (appointments.isEmpty()) "Appointments, nothing booked" else "Appointments")
        appointmentList()
    }
}

private val NAME_WIDTH = 160.dp

// ------------------------------------------ Month ------------------------------------------

/**
 * A month of day squares, Monday first, a row per week. Entries over more than a day, like time off, are bars from
 * their first day to their last, broken at the end of a row and picked up on the next. Each square has its
 * appointments and who's on shift
 * @param month Any day in the month showing, whose other days are greyed
 * @param onDay Opens a day by itself
 */
@Composable
internal fun CalendarMonth(
    month: LocalDate,
    appointments: List<Appointment>,
    schedule: List<ScheduleEntry>,
    onDay: (LocalDate) -> Unit,
    onEntry: ((ScheduleEntry) -> Unit)?
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    val (from, to) = AppointmentsHandler.rangeOf(org.mr0btain.ticketman.ui.screens.appointments.components.CalendarView.MONTH, month)
    val weeks = ((to.toEpochDays() - from.toEpochDays() + 1) / 7).toInt()
    val today = AppointmentsHandler.today()
    val booked = appointments.filter { it.status != AppointmentStatus.CANCELLED }.groupBy { dayOf(it.startsAt) }
    // Bars for what runs over more than a day, the rest listed in its square
    val spanning = schedule.filter { it.allDay || it.days(zone).size > 1 }
    val single = schedule.filter { it !in spanning }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth()) {
            days(from, 7).forEach { day ->
                Text(modifier = Modifier.weight(1f).padding(spacing.xs), text = weekdayLetters(day), style = TmTheme.typography.label, color = colors.textMuted)
            }
        }

        (0 until weeks).forEach { week ->
            val row = days(from.plus(DatePeriod(days = week * 7)), 7)
            val rowLanes = lanes(spansOf(spanning, row))
            val shown = rowLanes.take(MONTH_LANES)
            val hidden = rowLanes.drop(MONTH_LANES).flatten()

            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                // The squares, each opening its day
                Row(Modifier.matchParentSize()) {
                    row.forEach { day ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, colors.hairline)
                                .tmClickable(onClick = { onDay(day) }, shape = TmTheme.shapes.chip)
                        )
                    }
                }

                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { day ->
                            val isToday = day == today
                            Text(
                                modifier = Modifier.weight(1f).padding(horizontal = spacing.xs, vertical = 2.dp),
                                text = if (day.day == 1) "${day.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }} 1" else day.day.toString(),
                                style = if (isToday) TmTheme.typography.label else TmTheme.typography.caption,
                                color = when {
                                    isToday -> colors.accent.color
                                    day.month != month.month -> colors.textMuted
                                    else -> colors.text
                                }
                            )
                        }
                    }

                    shown.forEach { lane ->
                        LaneRow(lane, 7, MONTH_LANE) { span, modifier ->
                            TmScheduleBlock(
                                modifier = modifier,
                                title = "${span.item.who()}, ${span.item.kind.label.lowercase()}",
                                tone = colors.tone(span.item.kind.tone),
                                continuesBefore = span.before,
                                continuesAfter = span.after,
                                onClick = onEntry?.let { open -> { open(span.item) } }
                            )
                        }
                    }

                    // What's in each square: appointments by time, then who's on shift
                    Row(Modifier.fillMaxWidth().padding(bottom = spacing.xs)) {
                        row.forEach { day ->
                            val dayBooked = booked[day].orEmpty().sortedBy { it.startsAt }
                            val working = single.filter { it.isOn(day, zone) }
                            val more = hidden.count { it.start <= row.indexOf(day) && row.indexOf(day) < it.end }
                            Column(Modifier.weight(1f).padding(horizontal = spacing.xs), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                if (more > 0) Text(text = "+$more more off", style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 1)
                                dayBooked.take(MONTH_APPOINTMENTS).forEach { appointment ->
                                    Text(
                                        text = "${formatTime(appointment.startsAt)} ${appointment.customerName.ifBlank { "Booked" }}",
                                        style = TmTheme.typography.caption,
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (dayBooked.size > MONTH_APPOINTMENTS) {
                                    Text(text = "+${dayBooked.size - MONTH_APPOINTMENTS} more", style = TmTheme.typography.caption, color = colors.textMuted)
                                }
                                if (working.isNotEmpty()) {
                                    Text(
                                        text = working.joinToString(", ") { it.who().substringBefore(' ') },
                                        style = TmTheme.typography.caption,
                                        color = colors.accent.color,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MONTH_LANES = 3
private const val MONTH_APPOINTMENTS = 3

// ------------------------------------------ Staff board ------------------------------------------

/**
 * The staff board: a row per person and a column per day, each entry a block from its first day to its last, rows
 * growing to fit ones that overlap, and each day's scheduled hours under the row. The names stay put while the
 * days scroll sideways on a narrow window
 * @param staff The rows, with anyone else on the schedule added after them
 * @param onEmpty Writes a new entry for a person on a day, null when the schedule can't be changed here
 */
@Composable
internal fun StaffBoard(
    start: LocalDate,
    dayCount: Int,
    staff: List<User>,
    schedule: List<ScheduleEntry>,
    onEntry: ((ScheduleEntry) -> Unit)?,
    onEmpty: ((Uuid, LocalDate) -> Unit)?
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing
    val row = days(start, dayCount)
    val today = AppointmentsHandler.today()

    // Everyone with a row: the staff here, then anyone else on the schedule, like someone covering from elsewhere
    val people = staff.map { it.identifier to it.name.ifBlank { it.username } } +
        schedule.filter { entry -> staff.none { it.identifier == entry.userIdentifier } }
            .distinctBy { it.userIdentifier }
            .map { it.userIdentifier to it.who() }
    val byPerson = schedule.groupBy { it.userIdentifier }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val nameWidth = if (maxWidth < 600.dp) 112.dp else NAME_WIDTH
        val dayWidth = maxOf(DAY_MIN_WIDTH, (maxWidth - nameWidth) / dayCount)
        val heights = people.map { (identifier, _) -> LANE * lanes(spansOf(byPerson[identifier].orEmpty(), row)).size.coerceAtLeast(1) + HOURS_ROW }

        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Names, lined up with the rows
            Column(Modifier.width(nameWidth)) {
                Spacer(Modifier.height(HEADER_ROW))
                people.forEachIndexed { index, (_, name) ->
                    Box(Modifier.height(heights[index]).fillMaxWidth().border(0.5.dp, colors.hairline).padding(spacing.xs)) {
                        Text(text = name, style = TmTheme.typography.label, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(dayWidth * dayCount)) {
                    Row(Modifier.height(HEADER_ROW)) {
                        row.forEach { day ->
                            Column(Modifier.width(dayWidth).padding(horizontal = spacing.xs), verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "${weekdayLetters(day)} ${day.day}",
                                    style = TmTheme.typography.label,
                                    color = if (day == today) colors.accent.color else colors.text,
                                    maxLines = 1
                                )
                                if (day.day == 1 || day == row.first()) {
                                    Text(text = day.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() } + " ${day.year}", style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 1)
                                }
                            }
                        }
                    }

                    people.forEachIndexed { index, (identifier, _) ->
                        val entries = byPerson[identifier].orEmpty()
                        val personLanes = lanes(spansOf(entries, row))
                        val hours = ScheduleRules.hoursByDay(entries, zone)

                        Box(Modifier.height(heights[index]).fillMaxWidth()) {
                            // The day cells, empty ones adding an entry there
                            Row(Modifier.matchParentSize()) {
                                row.forEach { day ->
                                    Box(
                                        Modifier
                                            .width(dayWidth)
                                            .fillMaxHeight()
                                            .border(0.5.dp, colors.hairline)
                                            .then(if (onEmpty != null) Modifier.tmClickable(onClick = { onEmpty(identifier, day) }, shape = TmTheme.shapes.chip) else Modifier)
                                    )
                                }
                            }

                            Column(Modifier.fillMaxWidth()) {
                                personLanes.forEach { lane ->
                                    LaneRow(lane, dayCount, LANE) { span, modifier ->
                                        val entry = span.item
                                        TmScheduleBlock(
                                            modifier = modifier,
                                            title = entry.note.ifBlank { entry.kind.label },
                                            subtitle = entry.shortLabel(),
                                            tone = colors.tone(entry.kind.tone),
                                            continuesBefore = span.before,
                                            continuesAfter = span.after,
                                            onClick = onEntry?.let { open -> { open(entry) } }
                                        )
                                    }
                                }
                                if (personLanes.isEmpty()) Spacer(Modifier.height(LANE))

                                // Scheduled hours each day
                                Row(Modifier.height(HOURS_ROW)) {
                                    row.forEach { day ->
                                        Text(
                                            modifier = Modifier.width(dayWidth).padding(horizontal = spacing.xs),
                                            text = hours[day]?.let { ScheduleRules.hoursText(it) }.orEmpty(),
                                            style = TmTheme.typography.caption,
                                            color = colors.textMuted,
                                            maxLines = 1
                                        )
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

private val DAY_MIN_WIDTH = 96.dp
private val HEADER_ROW = 40.dp
private val HOURS_ROW = 18.dp
