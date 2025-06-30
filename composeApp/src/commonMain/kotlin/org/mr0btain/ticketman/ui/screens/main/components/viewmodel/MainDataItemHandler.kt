package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import kotlinx.coroutines.coroutineScope
import org.mr0btain.ticketman.components.network.database.DatabaseClient
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.data.InventoryItem
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.database.LocalDatabaseClient
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

@Suppress("PrivatePropertyName")
class MainDataItemHandler<T: MainDataItem>(
    val tag: String,
    private val viewModel: MainViewModel,
    private val localDatabaseClient: LocalDatabaseClient<T>,
    private val serverClient: DatabaseClient
) {
    // TODO: Don't know if we even have to handle logging as in the clients themselves they handle it
    private val ADD_TAG = "$tag:Add"
    private val ADD_ALL_TAG = "$tag:AddAll"
    private val UPDATE_TAG = "$tag:Update"
    private val REMOVE_TAG = "$tag:Remove"

    /**
     * Syncs data between server and local databases and updates view model data
     */
    suspend fun sync() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { serverClient.sync(it) }.let { items ->
                if (items != null && items.isNotEmpty()) {
                    when (items.first()) {
                        is CustomerData -> viewModel.bulkAddCustomers(items.filterIsInstance<CustomerData>())
                        is TicketInfo -> viewModel.bulkAddTickets(items.filterIsInstance<TicketInfo>())
                        is InventoryItem -> viewModel.bulkAddInventory(items.filterIsInstance<InventoryItem>())
                        is User -> viewModel.bulkAddUsers(items.filterIsInstance<User>())
                    }
                }
            }
        }
    }

    fun getNextId(): String? = localDatabaseClient.getNextId()

    suspend fun get(identifier: String): T? {
        return coroutineScope { localDatabaseClient.getById(identifier) }
    }

    suspend fun getAll(): List<T>? {
        return coroutineScope { serverClient.getAll() as List<T>? }
    }


    suspend fun add(item: T) {
        coroutineScope {
            try {
                localDatabaseClient.add(item)
                serverClient.send(item)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(ADD_TAG, SimpleError.FAIL)
            }

            AppLogger.m(ADD_TAG, SimpleError.SUCCESS)
        }
    }

    suspend fun addAll(data: List<T>) {
        coroutineScope {
            try {
                localDatabaseClient.addAll(data)
                serverClient.batchSend(data)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
            }

            AppLogger.m(ADD_ALL_TAG, SimpleError.SUCCESS)
        }
    }

    suspend fun update(item: T) {
        coroutineScope {
            try {
                localDatabaseClient.update(item)
                serverClient.update(item)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(UPDATE_TAG, SimpleError.FAIL)
            }

            AppLogger.m(UPDATE_TAG, SimpleError.SUCCESS)
        }
    }

    suspend fun remove(identifier: String) {
        coroutineScope {
            try {
                localDatabaseClient.remove(identifier)
                serverClient.remove(identifier)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(REMOVE_TAG, SimpleError.FAIL)
            }

            AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
        }
    }
}