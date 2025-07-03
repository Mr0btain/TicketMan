package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import kotlinx.coroutines.coroutineScope
import org.mr0btain.ticketman.components.network.database.InventoryServerDatabaseClientImpl
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.MainItemDataHandler
import org.mr0btain.ticketman.database.InventoryLocalDatabaseClientImpl

class InventoryDataHandlerImpl(
    private val localDatabaseClient: InventoryLocalDatabaseClientImpl,
    serverDatabaseClient: InventoryServerDatabaseClientImpl
) : MainItemDataHandler<InventoryItem>(localDatabaseClient, serverDatabaseClient) {
    override val TAG = "InventoryDataHandler"

    override suspend fun populateListFromDatabase() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { items -> setDataList(items) }
        }
    }
}