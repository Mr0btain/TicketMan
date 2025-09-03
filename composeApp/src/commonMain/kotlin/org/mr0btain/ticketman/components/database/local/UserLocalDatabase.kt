package org.mr0btain.ticketman.components.database.local

import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.database.UserLocalDatabaseImpl
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A Wrapper around LocalDatabaseClient mainly because we need to do some things differently with file storage than the server build.
 * Client side we have access to FileKit which makes this an absolute breeze.
 */
@OptIn(ExperimentalUuidApi::class)
class UserLocalDatabase(
    shouldLog: Boolean = false,
    private val localDatabaseImpl: UserLocalDatabaseImpl = UserLocalDatabaseImpl(shouldLog = shouldLog)
): ClientLocalDatabase<User>(localDatabaseImpl) {
    /** Adds a ticket to a user, assuming that ticket and user already exist in the database */
    fun addTicket(userId: Uuid, ticketId: Uuid) = localDatabaseImpl.addTicket(userId, ticketId)

    /** Gets all tickets for a user */
    fun getTickets(identifier: Uuid) = localDatabaseImpl.getTickets(identifier)
}