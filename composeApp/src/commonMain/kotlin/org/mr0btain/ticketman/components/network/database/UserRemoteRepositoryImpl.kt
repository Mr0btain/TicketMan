package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import org.mr0btain.ticketman.data.user.User

class UserRemoteRepositoryImpl(
    client: HttpClient
) : RemoteRepository<User>(
    tag = "UserRemoteRepositoryImpl",
    client = client,
    route = "api/data/user"
)