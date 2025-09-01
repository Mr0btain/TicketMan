package org.mr0btain.ticketman.components.database.local

import org.mr0btain.ticketman.data.Ticket
import org.mr0btain.ticketman.database.TicketLocalDatabaseClientImpl
import kotlin.uuid.ExperimentalUuidApi

/**
 * A Wrapper around LocalDatabaseClient mainly because we need to do some things differently with file storage than the server build.
 * Client side we have access to FileKit which makes this an absolute breeze.
 */
@OptIn(ExperimentalUuidApi::class)
class TicketLocalDatabase(
    shouldLog: Boolean = false,
    private val localDatabase: TicketLocalDatabaseClientImpl = TicketLocalDatabaseClientImpl(shouldLog)
): ClientLocalDatabase<Ticket>(localDatabase) {

}