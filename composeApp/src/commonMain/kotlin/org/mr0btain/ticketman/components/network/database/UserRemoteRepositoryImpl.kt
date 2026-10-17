package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.reflect.typeInfo
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.request.PasswordResetRequest
import kotlin.uuid.Uuid

@Suppress("PrivatePropertyName")
class UserRemoteRepositoryImpl(
    client: HttpClient
) : RemoteRepository<User>(
    tag = "UserRemoteRepositoryImpl",
    client = client,
    route = "api/user",
    itemType = typeInfo<User>(),
    listType = typeInfo<List<User>>()
) {
    private val GET_ME_TAG = "$tag:GetMe"
    private val RESET_PASSWORD_TAG = "$tag:ResetPassword"

    /** The logged in user */
    suspend fun getMe(): User? {
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(GET_ME_TAG) {
                client.get { url("$serverUrl/me") }
            }

            if (response != null) {
                handleHttpResponse<User>(GET_ME_TAG, response).onSuccess { return it }
            }
        }

        return null
    }

    /** Sets a new password for a user, admin only */
    suspend fun resetPassword(identifier: Uuid, password: String): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkErrorTryCatch(RESET_PASSWORD_TAG) {
            client.post {
                url("$serverUrl/password/$identifier")
                contentType(ContentType.Application.Json)
                setBody(PasswordResetRequest(password))
            }
        }

        return handleActionHttpResponse(RESET_PASSWORD_TAG, response)
    }
}
