package org.mr0btain.ticketman.ui.screens.appointments

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.mr0btain.ticketman.components.utils.formatTime
import org.mr0btain.ticketman.data.appointment.Appointment
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.containers.TmListRow
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.inputs.TmFieldPair
import org.mr0btain.ticketman.ui.kit.inputs.TmTextField
import org.mr0btain.ticketman.ui.kit.search.TmSearchField
import org.mr0btain.ticketman.ui.kit.selection.TmDropdown
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.tool
import ticketman.composeapp.generated.resources.user
import kotlin.time.Clock
import kotlin.time.Instant

/** Lengths offered for an appointment, in minutes */
private val LENGTHS = listOf(15, 30, 45, 60, 90, 120, 180, 240)

/** Days and times typed into the dialog, in this device's time zone */
object AppointmentTimes {
    private val zone get() = TimeZone.currentSystemDefault()

    fun toMillis(day: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime(day, LocalTime(hour, minute)).toInstant(zone).toEpochMilliseconds()

    /** The hour after now, kept to the working day */
    fun nextHour(): Int = (Clock.System.now().toLocalDateTime(zone).hour + 1).coerceIn(8, 18)

    /** Like "Mon Oct 26" */
    fun dayLabel(day: LocalDate): String {
        val weekday = day.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
        val month = day.month.name.take(3).lowercase().replaceFirstChar { it.titlecase() }
        return "$weekday $month ${day.day}"
    }

    fun day(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date

    /** "9:30" or "14:00" as hours and minutes, null when it isn't a time */
    fun parseTime(text: String): Pair<Int, Int>? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return (hour to minute).takeIf { hour in 0..23 && minute in 0..59 }
    }
}

/**
 * Books an appointment or changes one: when, for whom, the device and what's wrong, and who'll take it
 * @param onSave Gets the appointment, calls back with what went wrong or null once it's saved
 */
@Composable
fun AppointmentDialog(
    appointment: Appointment,
    isNew: Boolean,
    onSearchCustomers: suspend (String) -> List<Customer>,
    onSearchTechnicians: suspend (String) -> List<SearchResult>,
    onSave: (Appointment, (String?) -> Unit) -> Unit,
    onDismissRequest: () -> Unit
) {
    var day by remember { mutableStateOf(AppointmentTimes.day(appointment.startsAt).toString()) }
    var time by remember { mutableStateOf(formatTime(appointment.startsAt)) }
    var minutes by remember { mutableStateOf(appointment.minutes) }
    var customer by remember { mutableStateOf(appointment.customerIdentifier?.let { SearchResult(it, appointment.customerName) }) }
    var name by remember { mutableStateOf(if (appointment.customerIdentifier == null) appointment.customerName else "") }
    var phone by remember { mutableStateOf(appointment.phone) }
    var email by remember { mutableStateOf(appointment.email) }
    var manufacturer by remember { mutableStateOf(appointment.device.manufacturer) }
    var model by remember { mutableStateOf(appointment.device.model) }
    var problem by remember { mutableStateOf(appointment.problem) }
    var notes by remember { mutableStateOf(appointment.notes) }
    var technician by remember { mutableStateOf(appointment.technicianIdentifier?.let { SearchResult(it, appointment.technicianName) }) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val date = runCatching { LocalDate.parse(day.trim()) }.getOrNull()
    val clock = AppointmentTimes.parseTime(time)
    val startsAt = if (date != null && clock != null) AppointmentTimes.toMillis(date, clock.first, clock.second) else 0L

    val result = appointment.copy(
        startsAt = startsAt,
        minutes = minutes,
        customerIdentifier = customer?.identifier,
        customerName = customer?.text ?: name.trim(),
        phone = phone.trim(),
        email = email.trim(),
        device = appointment.device.copy(manufacturer = manufacturer.trim(), model = model.trim()),
        problem = problem.trim(),
        notes = notes.trim(),
        technicianIdentifier = technician?.identifier,
        technicianName = technician?.text.orEmpty()
    )
    val problemText = when {
        date == null -> "Enter the day like 2026-10-27"
        clock == null -> "Enter the time like 14:30"
        else -> result.problem()
    }

    TmDialog(
        title = if (isNew) "Book an appointment" else "Appointment",
        onDismissRequest = onDismissRequest,
        maxWidth = 560.dp,
        actions = {
            TmButtonRow(
                confirmText = if (isNew) "Book" else "Save",
                confirmEnabled = problemText == null,
                loading = saving,
                onCancel = onDismissRequest,
                onConfirm = {
                    saving = true
                    error = null
                    onSave(result) { problem ->
                        saving = false
                        error = problem
                    }
                }
            )
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)) {
            TmTextField(modifier = Modifier.weight(1f), value = day, onValueChange = { day = it }, label = "Day", placeholder = "2026-10-27")
            TmTextField(modifier = Modifier.weight(1f), value = time, onValueChange = { time = it }, label = "Time", placeholder = "14:30")
            TmDropdown(
                modifier = Modifier.weight(1f),
                label = "Length",
                options = LENGTHS + listOf(minutes).filter { it !in LENGTHS },
                selected = minutes,
                onSelect = { minutes = it },
                optionLabel = { if (it < 60) "$it min" else if (it % 60 == 0) "${it / 60} h" else "${it / 60} h ${it % 60} min" }
            )
        }

        PersonPicker(
            label = "Customer",
            picked = customer,
            placeholder = "Find a customer by name or phone",
            onSearch = { text -> onSearchCustomers(text).map { SearchResult(it.identifier, listOf(it.name, it.phone).filter { part -> part.isNotBlank() }.joinToString(", ")) } },
            onPick = { picked -> customer = picked?.let { SearchResult(it.identifier, it.text.substringBefore(", ")) } },
            icon = Res.drawable.user
        )

        if (customer == null) {
            Text(text = "Or someone new, added when they're checked in", style = TmTheme.typography.caption, color = TmTheme.colors.textMuted)
            TmTextField(modifier = Modifier.fillMaxWidth(), value = name, onValueChange = { name = it }, label = "Name")
            TmFieldPair(
                { TmTextField(modifier = it, value = phone, onValueChange = { v -> phone = v }, label = "Phone") },
                { TmTextField(modifier = it, value = email, onValueChange = { v -> email = v }, label = "Email") }
            )
        }

        TmFieldPair(
            { TmTextField(modifier = it, value = manufacturer, onValueChange = { v -> manufacturer = v }, label = "Make") },
            { TmTextField(modifier = it, value = model, onValueChange = { v -> model = v }, label = "Model") }
        )
        TmTextField(modifier = Modifier.fillMaxWidth(), value = problem, onValueChange = { problem = it }, label = "What's wrong", singleLine = false, minLines = 2)
        TmTextField(modifier = Modifier.fillMaxWidth(), value = notes, onValueChange = { notes = it }, label = "Notes", singleLine = false)

        PersonPicker(
            label = "Technician",
            picked = technician,
            placeholder = "Anyone",
            onSearch = onSearchTechnicians,
            onPick = { technician = it },
            icon = Res.drawable.tool
        )

        error?.let { TmNotice(text = it, kind = TmNoticeKind.ERROR) }
            ?: problemText?.let { TmNotice(text = it, kind = TmNoticeKind.WARNING) }
    }
}

/** Someone picked by searching, with a button to change them */
@Composable
private fun PersonPicker(
    label: String,
    picked: SearchResult?,
    placeholder: String,
    onSearch: suspend (String) -> List<SearchResult>,
    onPick: (SearchResult?) -> Unit,
    icon: DrawableResource
) {
    if (picked != null) {
        TmListRow(
            title = picked.text.ifBlank { label },
            subtitle = label,
            icon = icon,
            trailing = { TmButton(text = "Change", variant = TmButtonVariant.SECONDARY, onClick = { onPick(null) }) }
        )
        return
    }

    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            matches = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        matches = onSearch(query).take(5)
    }

    TmSearchField(modifier = Modifier.fillMaxWidth(), query = query, onQueryChange = { query = it }, label = label, placeholder = placeholder)
    matches.forEach { match ->
        TmListRow(title = match.text, icon = icon, onClick = { onPick(match) })
    }
}
