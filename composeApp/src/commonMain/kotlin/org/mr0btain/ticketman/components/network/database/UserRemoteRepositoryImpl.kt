package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.util.reflect.typeInfo
import org.mr0btain.ticketman.data.user.User

class UserRemoteRepositoryImpl(
    client: HttpClient
) : RemoteRepository<User>(
    tag = "UserRemoteRepositoryImpl",
    client = client,
    route = "api/user",
    itemType = typeInfo<User>(),
    listType = typeInfo<List<User>>()
)