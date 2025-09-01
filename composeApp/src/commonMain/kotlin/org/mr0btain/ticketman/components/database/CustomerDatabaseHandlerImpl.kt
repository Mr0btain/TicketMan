package org.mr0btain.ticketman.components.database

import kotlinx.coroutines.coroutineScope
import org.mr0btain.ticketman.components.network.database.CustomerServerDatabaseClientImpl
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.database.CustomerLocalDatabaseClientImpl
import org.mr0btain.ticketman.components.database.DatabaseHandler

class CustomerDatabaseHandlerImpl(
    private val localDatabaseClient: CustomerLocalDatabaseClientImpl,
    serverDatabaseClient: CustomerServerDatabaseClientImpl,
    imageDir: String
) : DatabaseHandler<Customer>("CustomerDataHandler", localDatabaseClient, serverDatabaseClient, imageDir) {
    override suspend fun populateListFromDatabase() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { items -> setDataList(items) }
        }
    }
}