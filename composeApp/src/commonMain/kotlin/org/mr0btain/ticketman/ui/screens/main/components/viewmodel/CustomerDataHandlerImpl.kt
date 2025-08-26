package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import kotlinx.coroutines.coroutineScope
import org.mr0btain.ticketman.components.network.database.CustomerServerDatabaseClientImpl
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.database.CustomerLocalDatabaseClientImpl

class CustomerDataHandlerImpl(
    private val localDatabaseClient: CustomerLocalDatabaseClientImpl,
    serverDatabaseClient: CustomerServerDatabaseClientImpl
) : MainItemDataHandler<Customer>(localDatabaseClient, serverDatabaseClient, "CustomerDataHandler") {
    override suspend fun populateListFromDatabase() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { items -> setDataList(items) }
        }
    }
}