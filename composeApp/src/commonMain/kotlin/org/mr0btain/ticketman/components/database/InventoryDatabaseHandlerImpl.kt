package org.mr0btain.ticketman.components.database

import kotlinx.coroutines.coroutineScope
import org.mr0btain.ticketman.components.network.database.InventoryServerDatabaseClientImpl
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.database.InventoryLocalDatabaseClientImpl

class InventoryDatabaseHandlerImpl(
    private val localDatabaseClient: InventoryLocalDatabaseClientImpl,
    serverDatabaseClient: InventoryServerDatabaseClientImpl,
    imageDir: String
) : DatabaseHandler<Inventory>("InventoryDataHandler", localDatabaseClient, serverDatabaseClient, imageDir) {
    override suspend fun populateListFromDatabase() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { items -> setDataList(items) }
        }
    }
}