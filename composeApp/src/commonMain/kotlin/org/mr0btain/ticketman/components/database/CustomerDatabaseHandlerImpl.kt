package org.mr0btain.ticketman.components.database

import org.mr0btain.ticketman.components.network.database.CustomerServerDatabaseClientImpl
import org.mr0btain.ticketman.data.Customer
import org.mr0btain.ticketman.database.CustomerLocalDatabaseImpl

class CustomerDatabaseHandlerImpl(
    private val localDatabaseClient: CustomerLocalDatabaseImpl,
    serverDatabaseClient: CustomerServerDatabaseClientImpl,
    imageDir: String
) : DatabaseHandler<Customer>("CustomerDataHandler", localDatabaseClient, serverDatabaseClient, imageDir) {

}