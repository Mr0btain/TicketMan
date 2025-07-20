package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import kotlinx.coroutines.coroutineScope
import org.mr0btain.ticketman.components.network.database.TicketServerDatabaseClientImpl
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.database.TicketLocalDatabaseClientImpl

class TicketDataHandlerImpl(
    private val localDatabaseClient: TicketLocalDatabaseClientImpl,
    serverDatabaseClient: TicketServerDatabaseClientImpl
) : MainItemDataHandler<TicketInfo>(localDatabaseClient, serverDatabaseClient, "TicketDataHandler") {
    override suspend fun populateListFromDatabase() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { items -> setDataList(items) }
        }
    }
}