package org.mr0btain.ticketman.ui.kit.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.utils.formatEnumName
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.ticket.TicketPriorityType
import org.mr0btain.ticketman.data.ticket.TicketStatusType
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

fun TmColors.tone(status: TicketStatusType): TmTone = when (status) {
    TicketStatusType.IN_PROGRESS -> warning
    TicketStatusType.ON_HOLD -> danger
    TicketStatusType.DONE -> success
    TicketStatusType.PICKED_UP -> neutral
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

/** What staff and customers call each status. DONE means the repair is finished and waiting to be collected */
val TicketStatusType.label: String
    get() = when (this) {
        TicketStatusType.IN_PROGRESS -> "In progress"
        TicketStatusType.ON_HOLD -> "On hold"
        TicketStatusType.DONE -> "Ready for pickup"
        TicketStatusType.PICKED_UP -> "Picked up"
    }

@Composable
fun StatusBadge(status: TicketStatusType, modifier: Modifier = Modifier, solid: Boolean = false) {
    TmBadge(text = status.label, tone = TmTheme.colors.tone(status), modifier = modifier, solid = solid)
}

@Composable
fun PriorityBadge(priority: TicketPriorityType, modifier: Modifier = Modifier) {
    TmBadge(text = formatEnumName(priority.name), tone = TmTheme.colors.tone(priority), modifier = modifier)
}

@Composable
fun AccessBadge(level: AccessLevel, modifier: Modifier = Modifier) {
    val text = if (level == AccessLevel.NONE) "Deactivated" else formatEnumName(level.name)
    TmBadge(text = text, tone = TmTheme.colors.tone(level), modifier = modifier, dot = false)
}

@Composable
fun UserStatusBadge(status: UserStatus, modifier: Modifier = Modifier) {
    TmBadge(text = formatEnumName(status.name), tone = TmTheme.colors.tone(status), modifier = modifier)
}
