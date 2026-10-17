package org.mr0btain.ticketman.ui.screens.main.expanded.ticket

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.data.search.SearchResult
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.screens.shared.PanelTopBar
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.screens.main.expanded.RecordsLayout
import kotlin.uuid.Uuid

// TODO: Add hours worked/ inventory items to a list of expense and charges
// TODO: Add invoice and ability to export

/**
 * Tickets: search and add on top, the list, and the picked ticket's details
 * @param selectedPanel 0 for my tickets, 1 for all of them
 */
@Composable
fun TicketPanel(
    selectedPanel: Int,
    onSelectedPanelChange: (Int) -> Unit,
    seed: TicketSeed,
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
                        options = listOf(0, 1),
                        selected = selectedPanel,
                        onSelect = onSelectedPanelChange,
                        optionLabel = { if (it == 0) "My tickets" else "All tickets" },
                        size = TmControlSize.MEDIUM
                    )
                }
            )
        },
        list = {
            TicketListPane(
                ticketList = ticketList,
                selectedTicket = seed.ticket.identifier,
                onClick = onSelectedTicketChange,
                state = ticketListState
            )
        },
        detail = {
            TicketInfoPane(seed = seed, onEditClick = onEditTicket, onImageClick = onImageClick)
        }
    )
}
