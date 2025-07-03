package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.network.database.ServerDatabaseClient
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.database.LocalDatabaseClient
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger

abstract class MainItemDataHandler<T: MainDataItem>(
    private val localDatabaseClient: LocalDatabaseClient,
    private val serverDatabaseClient: ServerDatabaseClient
) {
    // TODO: Don't know if we even have to handle logging as in the clients themselves they handle it
    abstract val TAG: String
    private val ADD_TAG = "${this.javaClass.simpleName}:Add"
    private val ADD_ALL_TAG = "${this.javaClass.simpleName}:AddAll"
    private val UPDATE_TAG = "${this.javaClass.simpleName}:Update"
    private val REMOVE_TAG = "${this.javaClass.simpleName}:Remove"
    private val UPDATE_OR_ADD_TAG = "${this.javaClass.simpleName}:UpdateOrAdd"

    private val _dataList: MutableStateFlow<List<T>> = MutableStateFlow(listOf())
    val dataList = _dataList.asStateFlow()

    private val _selectedId: MutableStateFlow<String> = MutableStateFlow("")
    val selectedId = _selectedId.asStateFlow()

    /**
     * Sets the data list
     */
    fun setDataList(data: List<T>) {
        _dataList.value = data
    }

    /**
     * Sets the selected Id
     */
    fun setSelectedId(identifier: String) {
        _selectedId.value = identifier
    }

    fun clearSelectedId() = { _selectedId.value = "" }

    /**
     * Adds or updates an item depending on weather it exists in the database or not
     */
    suspend fun addOrUpdate(data: T) {
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
    fun get(identifier: String): T? = _dataList.value.firstOrNull { it.identifier == identifier }

    /**
     * Gets data from local database and populates dataList with it
     */
    abstract suspend fun populateListFromDatabase()

    /**
     * Adds an item to the databases
     */
    suspend fun add(item: T) {
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
    suspend fun addAll(data: List<T>) {
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