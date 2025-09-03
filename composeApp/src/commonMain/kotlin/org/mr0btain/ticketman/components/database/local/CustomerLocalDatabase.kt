package org.mr0btain.ticketman.components.database.local

import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.database.CustomerLocalDatabaseImpl
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A Wrapper around LocalDatabaseClient mainly because we need to do some things differently with file storage than the server build.
 * Client side we have access to FileKit which makes this an absolute breeze.
 */
@OptIn(ExperimentalUuidApi::class)
class CustomerLocalDatabase(
    shouldLog: Boolean = false,
    private val localDatabaseImpl: CustomerLocalDatabaseImpl = CustomerLocalDatabaseImpl(shouldLog = shouldLog)
): ClientLocalDatabase<Customer>(localDatabaseImpl) {
    /** Adds a ticket to a customer, assuming that ticket and customer already exist in the database */
    fun addTicket(customerId: Uuid, ticketId: Uuid) = localDatabaseImpl.addTicket(customerId, ticketId)

    /** Gets all tickets for a customer */
    fun getTickets(identifier: Uuid) = localDatabaseImpl.getTickets(identifier)
}