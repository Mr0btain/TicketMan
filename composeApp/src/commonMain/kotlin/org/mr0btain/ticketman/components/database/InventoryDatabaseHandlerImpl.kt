package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.components.network.database.InventoryServerDatabaseClientImpl
import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.database.InventoryLocalDatabaseImpl

class InventoryDatabaseHandlerImpl(
    localDatabaseClient: InventoryLocalDatabaseImpl,
    serverDatabaseClient: InventoryServerDatabaseClientImpl,
    imageDir: String
) : DatabaseHandler<Inventory>("InventoryDataHandler", localDatabaseClient, serverDatabaseClient, imageDir) {

}