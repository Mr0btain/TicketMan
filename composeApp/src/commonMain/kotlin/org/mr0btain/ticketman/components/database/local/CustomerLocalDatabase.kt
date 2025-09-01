package org.mr0btain.ticketman.components.database.local

import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.database.CustomerLocalDatabaseClientImpl
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A Wrapper around LocalDatabaseClient mainly because we need to do some things differently with file storage than the server build.
 * Client side we have access to FileKit which makes this an absolute breeze.
 */
@OptIn(ExperimentalUuidApi::class)
class CustomerLocalDatabase(
    shouldLog: Boolean = false,
    private val localDatabase: CustomerLocalDatabaseClientImpl = CustomerLocalDatabaseClientImpl(shouldLog)
): ClientLocalDatabase<Customer>(localDatabase) {
    fun getTickets(identifier: Uuid) = localDatabase.getTickets(identifier)
}