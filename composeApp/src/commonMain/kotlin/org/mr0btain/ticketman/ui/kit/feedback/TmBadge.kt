package org.mr0btain.ticketman.ui.kit.feedback

import org.mr0btain.ticketman.data.notify.OutboxStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.mr0btain.ticketman.ui.kit.theme.TmColors
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone

/**
 * Small colored label for a state, like a ticket's status
 * @param solid Fills with the tone's color instead of its faint version, for when it has to stand out
 * @param dot Shows a dot before the text so the color reads even at a glance
 */
@Composable
fun TmBadge(
    text: String,
    tone: TmTone,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    dot: Boolean = true
) {
    val content = if (solid) tone.onColor else tone.color

    Row(
        modifier = modifier
            .height(22.dp)
            .background(if (solid) tone.color else tone.soft, TmTheme.shapes.chip)
            .padding(horizontal = TmTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot) TmDot(color = content)

        Text(
            text = text,
            style = TmTheme.typography.caption,
            color = content,
            maxLines = 1
        )
    }
}

/** Little round marker in a tone's color, for lists and dropdown options */
@Composable
fun TmDot(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(modifier.size(7.dp).background(color, TmTheme.shapes.pill))
}

// --------------------------------- What each state looks like ---------------------------------

fun TmColors.tone(tone: StatusTone): TmTone = when (tone) {
    StatusTone.ACCENT -> accent
    StatusTone.SUCCESS -> success
    StatusTone.WARNING -> warning
    StatusTone.DANGER -> danger
    StatusTone.NEUTRAL -> neutral
}

fun TmColors.tone(status: TicketStatusType): TmTone = tone(status.tone)

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
