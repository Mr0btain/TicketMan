package org.mr0btain.ticketman.ui.kit.selection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.mr0btain.ticketman.data.ticket.StatusChoice
import org.mr0btain.ticketman.data.ticket.TicketStatusType
import org.mr0btain.ticketman.data.ticket.label
import org.mr0btain.ticketman.data.ticket.selectable
import org.mr0btain.ticketman.ui.kit.feedback.LocalTicketStatuses
import org.mr0btain.ticketman.ui.kit.feedback.TmDot
import org.mr0btain.ticketman.ui.kit.feedback.statusTone

/**
 * Picks a ticket's status, the shop's own ones from [LocalTicketStatuses] included. Buttons side by side while
 * there are only the built-in ones, a dropdown once the shop has added its own
 * @param dropdown Always a dropdown, for forms where buttons wouldn't line up
 */
@Composable
fun TmStatusPicker(
    selected: StatusChoice,
    onSelect: (StatusChoice) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Status",
    dropdown: Boolean = false
) {
    val statuses = LocalTicketStatuses.current

    // A picked up ticket is closed, the server won't change its status
    val options = if (selected.status == TicketStatusType.PICKED_UP) listOf(selected) else statuses.selectable(current = selected)

    if (dropdown || options.size > TicketStatusType.selectable.size) {
        TmDropdown(
            modifier = modifier,
            label = label,
            options = options,
            selected = selected,
            onSelect = { if (it != selected) onSelect(it) },
            optionLabel = { statuses.label(it) },
            optionLeading = { TmDot(statusTone(it).color) }
        )
    } else {
        TmSegmented(
            modifier = modifier,
            label = label,
            options = options,
            selected = selected,
            onSelect = { if (it != selected) onSelect(it) },
            optionLabel = { statuses.label(it) }
        )
    }
}
