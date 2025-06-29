package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.logging.AppLogger
import org.mr0btain.ticketman.data.MainDataItem
import org.mr0btain.ticketman.data.customer.CustomerData
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel

class CustomerHandler(
    override val TAG: String = "CustomerHandler",
    private val viewModel: MainViewModel
): DataHandler {
    // Customer data list
    private val _data: MutableStateFlow<List<CustomerData>> = MutableStateFlow(listOf())
    val data = _data.asStateFlow()

    // Selected Customer
    private val _selected: MutableStateFlow<String> = MutableStateFlow("")
    val selected = _selected.asStateFlow()

    override fun setSelectedIdentifier(identifier: String) {
        _selected.value = identifier
    }

    // TODO: Really need to find a way to make sure these don't clash. Maybe switch to UUID and have server handle conflicts?
    /**
     * Retrieves the next identifier for a new customer
     */
    override fun getNextIdentifier(): String? {
        return _data.value.maxOfOrNull { it.identifier }
    }

    /**
     * Gets customer data for a specific identifier
     */
    override fun getForIdentifier(identifier: String): CustomerData? {
        return _data.value.firstOrNull { it.info.identifier == identifier }
    }

    /**
     * Updates or adds a customer depending on if it exists in the database or not
     */
    override fun updateOrAdd(item: MainDataItem) {
        if (item is CustomerData) {
            viewModel.viewModelScope.launch {
                // Check if customer exists or not, if not then add it
                if (_data.value.firstOrNull { it.info.identifier == item.info.identifier } != null) {
                    // Update data in viewmodel
                    // Get index of the item
                    val index = _data.value.indexOfFirst { it.info.identifier == item.info.identifier }

                    // update list with new data
                    _data.value = _data.value.toMutableList().also { it[index] = item }

                    // Update item in databases
                    viewModel.updateItemInDatabase(item)
                } else {
                    // Else add to list in view model
                    _data.value = _data.value + item

                    // Add item to databases
                    viewModel.addItemToDatabase(item)
                }

                // Set selected customer
                _selected.value = item.identifier

                AppLogger.m("$TAG:UpdateOrAdd", "SUCCESS")
            }
        } else AppLogger.m("$TAG:UpdateOrAdd", "FAIL: Item is not CustomerData!")
    }

    /**
     * Gets all customer data
     */
    override fun getAll(): List<CustomerData> {
        return _data.value
    }

    // TODO: Handle server end of this as well
    /**
     * Removes a customer from the database entirely
     */
    override fun remove(item: MainDataItem) {
        viewModel.viewModelScope.launch {
            // Check if customer exists or not
            if (data.value.map { it.info.identifier }.contains(item.identifier)) {
                // update in memory data
                _data.value = _data.value.filter { it.info.identifier != item.identifier }

                // Update database
                viewModel.removeItemFromDatabase(item)
            } else {
                AppLogger.i("$TAG:remove", SimpleError.FAIL)
            }
        }
    }
}