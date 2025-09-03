package org.mr0btain.ticketman.components.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.network.database.UserServerDatabaseClientImpl
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.database.UserLocalDatabaseImpl
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UserDatabaseHandlerImpl(
    private val localDatabaseClient: UserLocalDatabaseImpl,
    serverDatabaseClient: UserServerDatabaseClientImpl,
    imageDir: String
) : DatabaseHandler<User>("UserDataHandler", localDatabaseClient, serverDatabaseClient, imageDir) {
    private val _current: MutableStateFlow<User> = MutableStateFlow(User())
    val current = _current.asStateFlow()

    /**
     * Sets current user
     */
    fun setCurrent(user: User) {
        _current.value = user
    }


}