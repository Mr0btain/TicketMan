package org.mr0btain.ticketman.components.database.local

import org.mr0btain.ticketman.data.Inventory
import org.mr0btain.ticketman.database.InventoryLocalDatabaseImpl
import kotlin.uuid.ExperimentalUuidApi

/**
 * A Wrapper around LocalDatabaseClient mainly because we need to do some things differently with file storage than the server build.
 * Client side we have access to FileKit which makes this an absolute breeze.
 */
@OptIn(ExperimentalUuidApi::class)
class InventoryLocalDatabase(
    shouldLog: Boolean = false,
    localDatabaseImpl: InventoryLocalDatabaseImpl = InventoryLocalDatabaseImpl(shouldLog = shouldLog)
): ClientLocalDatabase<Inventory>(localDatabaseImpl) {

}