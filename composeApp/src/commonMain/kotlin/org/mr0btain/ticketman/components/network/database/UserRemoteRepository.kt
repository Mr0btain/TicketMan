package org.mr0btain.ticketman.components.network.database

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.components.network.ServerError
import org.mr0btain.ticketman.components.network.handleActionHttpResponse
import org.mr0btain.ticketman.components.network.handleDataHttpResponse
import org.mr0btain.ticketman.components.network.handleHttpResponse
import org.mr0btain.ticketman.components.network.networkErrorTryCatch
import org.mr0btain.ticketman.components.network.networkRequest
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.error.EmptyResult
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.onSuccess
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.NetworkError
import org.mr0btain.ticketman.server.UserPaths
import org.mr0btain.ticketman.server.fill
import org.mr0btain.ticketman.server.request.AccountSetupRequest
import org.mr0btain.ticketman.server.request.ApproveRequest
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.request.PasswordResetRequest
import org.mr0btain.ticketman.server.request.SetPinRequest
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.server.response.PendingUserResponse
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.server.request.UserPhotoRequest
import org.mr0btain.ticketman.server.response.BadgeResponse
import kotlin.uuid.Uuid

@Suppress("PrivatePropertyName")
class UserRemoteRepository(
    client: HttpClient
) : RemoteRepository<User>(
    tag = "UserRemoteRepository",
    client = client,
    route = ApiRoutes.USER,
    types = itemTypes<User>()
) {
    private val GET_ME_TAG = "$tag:GetMe"
    private val RESET_PASSWORD_TAG = "$tag:ResetPassword"
    private val SETUP_TAG = "$tag:Setup"
    private val PENDING_TAG = "$tag:Pending"
    private val APPROVE_TAG = "$tag:Approve"
    private val REJECT_TAG = "$tag:Reject"
    private val INVITE_TAG = "$tag:Invite"
    private val PIN_TAG = "$tag:Pin"
    private val PHOTO_TAG = "$tag:Photo"
    private val BADGE_TAG = "$tag:Badge"

    /** The logged in user */
    suspend fun getMe(): User? {
        if (serverUrl.isNotBlank()) {
            val response = networkErrorTryCatch(GET_ME_TAG) {
                client.get { url("$serverUrl${UserPaths.ME}") }
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

        val response = networkRequest(RESET_PASSWORD_TAG) {
            client.post {
                url("$serverUrl${UserPaths.PASSWORD.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(PasswordResetRequest(password))
            }
        }

        return handleActionHttpResponse(RESET_PASSWORD_TAG, response)
    }

    /** Changes your own PIN, needs your password */
    suspend fun setOwnPin(currentPassword: String, pin: String): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(PIN_TAG) {
            client.post {
                url("$serverUrl${UserPaths.ME_PIN}")
                contentType(ContentType.Application.Json)
                setBody(SetPinRequest(currentPassword, pin))
            }
        }

        return handleActionHttpResponse(PIN_TAG, response)
    }

    /** Lets a PIN that locked from wrong tries work again, admin only */
    suspend fun unlockPin(identifier: Uuid): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(PIN_TAG) {
            client.post { url("$serverUrl${UserPaths.PIN_UNLOCK.fill(identifier)}") }
        }

        return handleActionHttpResponse(PIN_TAG, response)
    }

    /** Clears a user's PIN so they have to set a new one, admin only */
    suspend fun resetPin(identifier: Uuid): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(PIN_TAG) {
            client.delete { url("$serverUrl${UserPaths.PIN.fill(identifier)}") }
        }

        return handleActionHttpResponse(PIN_TAG, response)
    }

    /** Finishes first login setup after an approved request */
    suspend fun completeSetup(request: AccountSetupRequest): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(SETUP_TAG) {
            client.post {
                url("$serverUrl${UserPaths.SETUP}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

        return handleActionHttpResponse(SETUP_TAG, response)
    }

    /** Requests and invites waiting on someone, admin only */
    suspend fun getPending(): List<PendingUserResponse> {
        if (serverUrl.isBlank()) return emptyList()

        val response = networkErrorTryCatch(PENDING_TAG) {
            client.get { url("$serverUrl${UserPaths.PENDING}") }
        } ?: return emptyList()

        handleHttpResponse<List<PendingUserResponse>>(PENDING_TAG, response).onSuccess { return it }
        return emptyList()
    }

    suspend fun approve(identifier: Uuid, accessLevel: AccessLevel): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(APPROVE_TAG) {
            client.post {
                url("$serverUrl${UserPaths.APPROVE.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(ApproveRequest(accessLevel))
            }
        }

        return handleActionHttpResponse(APPROVE_TAG, response)
    }

    /** Turns down a request or cancels an invite */
    suspend fun reject(identifier: Uuid): EmptyResult<ServerError> {
        if (serverUrl.isBlank()) return noServerError

        val response = networkRequest(REJECT_TAG) {
            client.delete { url("$serverUrl${UserPaths.REJECT.fill(identifier)}") }
        }

        return handleActionHttpResponse(REJECT_TAG, response)
    }

    suspend fun invite(request: InviteRequest): TmResult<InviteResponse, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(INVITE_TAG) {
            client.post {
                url("$serverUrl${UserPaths.INVITE}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

        return handleDataHttpResponse(INVITE_TAG, response)
    }

    /**
     * Sets a user's profile photo, one just uploaded or the one they have, or takes it off with null. Admins for
     * anyone, everyone else for themselves
     * @return The user with the photo
     */
    suspend fun setPhoto(identifier: Uuid, photo: StoredImage?): TmResult<User, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(PHOTO_TAG) {
            client.post {
                url("$serverUrl${UserPaths.SET_PHOTO.fill(identifier)}")
                contentType(ContentType.Application.Json)
                setBody(UserPhotoRequest(photo))
            }
        }

        return handleDataHttpResponse(PHOTO_TAG, response)
    }

    /** Admin only: the user's ID card code, `NOT_FOUND` when they don't have one yet */
    suspend fun getBadge(identifier: Uuid): TmResult<BadgeResponse, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(BADGE_TAG) { client.get { url("$serverUrl${UserPaths.BADGE.fill(identifier)}") } }
        return handleDataHttpResponse(BADGE_TAG, response)
    }

    /** Admin only: a new ID card code, which stops the old card working */
    suspend fun issueBadge(identifier: Uuid): TmResult<BadgeResponse, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(BADGE_TAG) { client.post { url("$serverUrl${UserPaths.BADGE.fill(identifier)}") } }
        return handleDataHttpResponse(BADGE_TAG, response)
    }

    /** New setup code for an invite, the old one stops working */
    suspend fun renewInvite(identifier: Uuid): TmResult<InviteResponse, ServerError> {
        if (serverUrl.isBlank()) return TmResult.Error(ServerError(NetworkError.NO_INTERNET, "No server set"))

        val response = networkRequest(INVITE_TAG) {
            client.post { url("$serverUrl${UserPaths.INVITE_RENEW.fill(identifier)}") }
        }

        return handleDataHttpResponse(INVITE_TAG, response)
    }
}
