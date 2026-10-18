package org.mr0btain.ticketman.ui.screens.dashboard.components.ticket

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.ticket.TicketScope
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.screens.shared.PanelTopBar
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.screens.dashboard.components.RecordsLayout
import kotlin.uuid.Uuid

// TODO: Add hours worked/ inventory items to a list of expense and charges
// TODO: Add invoice and ability to export

/**
 * Tickets: search and add on top, the list, and the picked ticket's details
 * @param ticketScope Whether the list shows the user's tickets or everyone's
 */
@Composable
fun TicketPanel(
    ticketScope: TicketScope,
    onTicketScopeChange: (TicketScope) -> Unit,
    seed: TicketSeed,
    pendingId: Uuid? = null,
    ticketList: List<Ticket>,
    onAddNewTicket: () -> Unit,
    onEditTicket: () -> Unit,
    onSelectedTicketChange: (Uuid) -> Unit,
    onSearchQuery: suspend (String) -> List<SearchResult>,
    onDeleteTicket: () -> Unit,
    onImageClick: (Uuid) -> Unit,
    ticketListState: LazyListState
) {
    RecordsLayout(
        toolbar = {
            PanelTopBar(
                label = "Ticket",
                onSearchClick = onSelectedTicketChange,
                onSearchQuery = onSearchQuery,
                onAddClick = onAddNewTicket,
                onDeleteClick = onDeleteTicket,
                canDelete = !seed.isNew,
                extras = {
                    TmSegmented(
                        options = TicketScope.entries,
                        selected = ticketScope,
                        onSelect = onTicketScopeChange,
                        optionLabel = { it.label },
                        size = TmControlSize.MEDIUM
                    )
                }
            )
        },
        list = {
            TicketListPane(
                ticketList = ticketList,
                ticketScope = ticketScope,
                selectedTicket = pendingId ?: seed.identifier,
                onClick = onSelectedTicketChange,
                state = ticketListState
            )
        },
        detail = {
            TicketInfoPane(seed = seed, onEditClick = onEditTicket, onImageClick = onImageClick)
        }
    )
}
