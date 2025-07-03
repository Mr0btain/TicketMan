package org.mr0btain.ticketman.ui.screens.main.components.viewmodel

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.network.database.UserServerDatabaseClientImpl
import org.mr0btain.ticketman.ui.screens.main.components.viewmodel.MainItemDataHandler
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.database.UserLocalDatabaseClientImpl

class UserDataHandlerImpl(
    private val localDatabaseClient: UserLocalDatabaseClientImpl,
    serverDatabaseClient: UserServerDatabaseClientImpl
) : MainItemDataHandler<User>(localDatabaseClient, serverDatabaseClient) {
    override val TAG = "UserDataHandler"

    private val _current: MutableStateFlow<User> = MutableStateFlow(User())
    val current = _current.asStateFlow()

    /**
     * Sets current user
     */
    fun setCurrent(user: User) {
        _current.value = user
    }

    override suspend fun populateListFromDatabase() {
        coroutineScope {
            localDatabaseClient.getAll()?.let { items -> setDataList(items) }
        }
    }
}
