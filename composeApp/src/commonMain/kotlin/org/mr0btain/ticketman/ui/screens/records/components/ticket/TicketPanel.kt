package org.mr0btain.ticketman.ui.screens.records.components.ticket

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import org.mr0btain.ticketman.components.data.ticket.TicketScope
import org.mr0btain.ticketman.components.data.ticket.TicketSeed
import org.mr0btain.ticketman.data.search.TmSearchResult
import org.mr0btain.ticketman.data.ticket.Ticket
import org.mr0btain.ticketman.ui.kit.navigation.TmPanelTopBar
import org.mr0btain.ticketman.ui.kit.selection.TmSegmented
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.screens.records.components.RecordsLayout
import kotlin.uuid.Uuid


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
    canEdit: Boolean = true,
    onSelectedTicketChange: (Uuid) -> Unit,
    onSearchQuery: suspend (String) -> List<TmSearchResult>,
    onDeleteTicket: () -> Unit,
    onImageClick: (noteIdentifier: Uuid, page: Int) -> Unit,
    ticketListState: LazyListState,
    loadError: String? = null,
    work: @Composable () -> Unit = {}
) {
    RecordsLayout(
        toolbar = {
            TmPanelTopBar(
                label = "Ticket",
                onSearchClick = onSelectedTicketChange,
                onSearchQuery = onSearchQuery,
                onAddClick = onAddNewTicket,
                onDeleteClick = onDeleteTicket,
                canDelete = !seed.isNew,
                canChange = canEdit,
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
                state = ticketListState,
                loadError = loadError
            )
        },
        detail = {
            TicketInfoPane(seed = seed, onEditClick = onEditTicket, canEdit = canEdit, onImageClick = onImageClick, work = work)
        }
    )
}
