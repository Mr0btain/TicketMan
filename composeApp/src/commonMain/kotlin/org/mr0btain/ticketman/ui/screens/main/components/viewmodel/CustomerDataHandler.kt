package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.network.database.CustomerServerDatabaseClientImpl
import org.mr0btain.ticketman.data.CustomerData
import org.mr0btain.ticketman.database.CustomerLocalDatabaseClientImpl
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

/**
 * Handles all the data interactions we may need. Mostly here to help organize this mess in the view model
 */
class CustomerDataHandler(
    private val localDatabaseClient: CustomerLocalDatabaseClientImpl,
    private val serverDatabaseClient: CustomerServerDatabaseClientImpl
) {
    // TODO: Don't know if we even have to handle logging as in the clients themselves they handle it
    private val TAG = "CustomerDataHandler"
    private val ADD_TAG = "$TAG:Add"
    private val ADD_ALL_TAG = "$TAG:AddAll"
    private val UPDATE_TAG = "$TAG:Update"
    private val REMOVE_TAG = "$TAG:Remove"
    private val UPDATE_OR_ADD_TAG = "$TAG:UpdateOrAdd"

    private val _dataList: MutableStateFlow<List<CustomerData>> = MutableStateFlow(listOf())
    val dataList = _dataList.asStateFlow()

    private val _selectedId: MutableStateFlow<String> = MutableStateFlow("")
    val selectedId = _selectedId.asStateFlow()

    /**
     * Adds or updates an item depending on weather it exists in the database or not
     */
    suspend fun addOrUpdate(data: CustomerData) {
        coroutineScope {
            // Check if customer exists or not, if not then add it
            if (_dataList.value.firstOrNull { it.identifier == data.identifier } != null) {
                // Update data in viewmodel
                // Get index of the item
                val index = _dataList.value.indexOfFirst { it.identifier == data.identifier }

                // update list with new data
                _dataList.value = dataList.value.toMutableList().also { it[index] = data }

                // Update database
                if (localDatabaseClient.update(data)) {
                    serverDatabaseClient.update(data)
                    AppLogger.m(UPDATE_OR_ADD_TAG, SimpleError.SUCCESS)
                } else AppLogger.i(UPDATE_OR_ADD_TAG, SimpleError.FAIL)
            } else {
                // Else add to list in view model
                _dataList.value = dataList.value + data

                // Add to database
                if (localDatabaseClient.add(data)) {
                    serverDatabaseClient.send(data)
                    AppLogger.m(UPDATE_OR_ADD_TAG, SimpleError.SUCCESS)
                } else AppLogger.i(UPDATE_OR_ADD_TAG, SimpleError.FAIL)
            }

            // Set selected customer
            _selectedId.value = data.identifier
        }
    }

    /**
     * Syncs data between server and local databases
     */
    suspend fun sync() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { serverDatabaseClient.sync(it) }
        }
    }

    /**
     * Gets the next identifier for an item
     */
    fun getNextId(): String? = localDatabaseClient.getNextId()

    /**
     * Gets an item from the list by identifier
     */
    fun get(identifier: String): CustomerData? = _dataList.value.firstOrNull { it.identifier == identifier }

    /**
     * Gets data from local database and populates dataList with it
     */
    suspend fun populateListFromDatabase() {
        coroutineScope {
            localDatabaseClient.getAll()?.let {
                    items -> _dataList.value = items
            }
        }
    }

    /**
     * Adds an item to the databases
     */
    suspend fun add(item: CustomerData) {
        coroutineScope {
            try {
                _dataList.value += item
                localDatabaseClient.add(item)
                serverDatabaseClient.send(item)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(ADD_TAG, SimpleError.FAIL)
            }

            AppLogger.m(ADD_TAG, SimpleError.SUCCESS)
        }
    }

    /**
     * Adds a list of items to the database
     */
    suspend fun addAll(data: List<CustomerData>) {
        coroutineScope {
            try {
                _dataList.value += data
                localDatabaseClient.addAll(data)
                serverDatabaseClient.batchSend(data)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(ADD_ALL_TAG, SimpleError.FAIL)
            }

            AppLogger.m(ADD_ALL_TAG, SimpleError.SUCCESS)
        }
    }

    /**
     * Removes an item from the database
     */
    suspend fun remove(identifier: String) {
        coroutineScope {
            try {
                _dataList.value = dataList.value.filter { it.identifier != identifier }
                localDatabaseClient.remove(identifier)
                serverDatabaseClient.remove(identifier)
            } catch (ex: Exception) {
                ex.printStackTrace()
                AppLogger.i(REMOVE_TAG, SimpleError.FAIL)
            }

            AppLogger.m(REMOVE_TAG, SimpleError.SUCCESS)
        }
    }
}