package org.mr0btain.ticketman.components.database

import kotlinx.coroutines.coroutineScope
import org.mr0btain.ticketman.components.network.database.TicketServerDatabaseClientImpl
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.database.TicketLocalDatabaseClientImpl

class TicketDatabaseHandlerImpl(
    private val localDatabaseClient: TicketLocalDatabaseClientImpl,
    serverDatabaseClient: TicketServerDatabaseClientImpl,
    imageDir: String
) : DatabaseHandler<Ticket>("TicketDataHandler", localDatabaseClient, serverDatabaseClient, imageDir) {
    override suspend fun populateListFromDatabase() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { items -> setDataList(items) }
        }
    }
}