package org.mr0btain.ticketman.ui.kit.feedback

import org.mr0btain.uikit.feedback.TmBadge
import kotlin.uuid.Uuid
import org.mr0btain.ticketman.data.location.Location
import org.mr0btain.ticketman.data.appointment.AppointmentStatus
import org.mr0btain.ticketman.data.notify.OutboxStatus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.CustomStatus
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.StatusTone
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.label
import org.mr0btain.ticketman.data.ticket.tone
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.uikit.theme.TmColors
import org.mr0btain.uikit.theme.TmTheme
import org.mr0btain.uikit.theme.TmTone

// --------------------------------- What each state looks like ---------------------------------

fun TmColors.tone(tone: StatusTone): TmTone = when (tone) {
    StatusTone.ACCENT -> accent
    StatusTone.SUCCESS -> success
    StatusTone.WARNING -> warning
    StatusTone.DANGER -> danger
    StatusTone.NEUTRAL -> neutral
}

fun TmColors.tone(status: TicketStatusType): TmTone = tone(status.tone)

fun TmColors.tone(status: AppointmentStatus): TmTone = when (status) {
    AppointmentStatus.BOOKED -> accent
    AppointmentStatus.ARRIVED -> success
    AppointmentStatus.NO_SHOW -> warning
    AppointmentStatus.CANCELLED -> neutral
}

fun TmColors.tone(priority: TicketPriorityType): TmTone = when (priority) {
    TicketPriorityType.LOW -> neutral
    TicketPriorityType.NORMAL -> accent
    TicketPriorityType.HIGH -> warning
    TicketPriorityType.URGENT -> danger
}

fun TmColors.tone(level: AccessLevel): TmTone = when (level) {
    AccessLevel.ADMIN -> accent
    AccessLevel.NORMAL -> success
    AccessLevel.RESTRICTED -> neutral
    AccessLevel.NONE -> danger
}

fun TmColors.tone(status: UserStatus): TmTone = when (status) {
    UserStatus.PENDING -> warning
    UserStatus.ACTIVE -> success
    UserStatus.DEACTIVATED -> neutral
}

fun TmColors.tone(status: OutboxStatus): TmTone = when (status) {
    OutboxStatus.PENDING -> warning
    OutboxStatus.SENT -> success
    OutboxStatus.FAILED -> danger
    OutboxStatus.CANCELLED -> neutral
}

/** The shop's own ticket statuses, from its profile, so status badges and pickers anywhere below can name them */
val LocalTicketStatuses = staticCompositionLocalOf { emptyList<CustomStatus>() }

/** A status's name as staff see it, with the shop's own statuses from [LocalTicketStatuses] */
@Composable
fun statusLabel(choice: StatusChoice): String = LocalTicketStatuses.current.label(choice)

@Composable
fun statusTone(choice: StatusChoice): TmTone = TmTheme.colors.tone(LocalTicketStatuses.current.tone(choice))

/** A built-in status on its own, for the gallery and anywhere the shop's own statuses don't apply */
@Composable
fun TmStatusBadge(status: TicketStatusType, modifier: Modifier = Modifier, solid: Boolean = false) {
    TmBadge(text = status.label, tone = TmTheme.colors.tone(status), modifier = modifier, solid = solid)
}

/** A ticket's status, named and colored as the shop set it up */
@Composable
fun TmStatusBadge(status: StatusChoice, modifier: Modifier = Modifier, solid: Boolean = false) {
    TmBadge(text = statusLabel(status), tone = statusTone(status), modifier = modifier, solid = solid)
}

@Composable
fun TmPriorityBadge(priority: TicketPriorityType, modifier: Modifier = Modifier) {
    TmBadge(text = formatEnumName(priority.name), tone = TmTheme.colors.tone(priority), modifier = modifier)
}

@Composable
fun TmAccessBadge(level: AccessLevel, modifier: Modifier = Modifier) {
    val text = if (level == AccessLevel.NONE) "Deactivated" else formatEnumName(level.name)
    TmBadge(text = text, tone = TmTheme.colors.tone(level), modifier = modifier, dot = false)
}

@Composable
fun TmUserStatusBadge(status: UserStatus, modifier: Modifier = Modifier) {
    TmBadge(text = formatEnumName(status.name), tone = TmTheme.colors.tone(status), modifier = modifier)
}

/**
 * The shop's locations and the one this device works at, for [TmLocationBadge]
 * @param working The location being worked at, null when looking at every one
 */
data class TmLocations(
    val all: List<Location> = emptyList(),
    val working: Uuid? = null
) {
    /** More than one in use, so it's worth saying where something is */
    val several: Boolean
        get() = all.count { !it.retired } > 1
}

/** The shop's locations, provided in `App` */
val LocalLocations = staticCompositionLocalOf { TmLocations() }

/**
 * Where a record is, by its location's name: only when the shop has more than one location, and unless [always],
 * only when it's somewhere other than where this device works. Nothing otherwise
 */
@Composable
fun TmLocationBadge(locationIdentifier: Uuid?, modifier: Modifier = Modifier, always: Boolean = false) {
    val locations = LocalLocations.current
    if (!locations.several) return

    // Records from before locations belong to the main one
    val location = locations.all.firstOrNull { it.identifier == locationIdentifier } ?: locations.all.firstOrNull { it.main } ?: return
    if (!always && location.identifier == locations.working) return

    TmBadge(text = location.name, tone = TmTheme.colors.neutral, modifier = modifier, dot = false)
}
