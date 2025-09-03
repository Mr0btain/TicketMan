package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.components.network.database.TicketServerDatabaseClientImpl
import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.database.TicketLocalDatabaseImpl

class TicketDatabaseHandlerImpl(
    private val localDatabaseClient: TicketLocalDatabaseImpl,
    serverDatabaseClient: TicketServerDatabaseClientImpl,
    imageDir: String
) : DatabaseHandler<Ticket>("TicketDataHandler", localDatabaseClient, serverDatabaseClient, imageDir) {

}