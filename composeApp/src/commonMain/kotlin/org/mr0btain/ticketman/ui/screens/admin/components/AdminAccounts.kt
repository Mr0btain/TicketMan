package org.mr0btain.ticketman.ui.screens.admin.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.isNotFound
import org.mr0btain.ticketman.components.print.PrintBlock
import org.mr0btain.ticketman.components.print.PrintTemplates
import org.mr0btain.ticketman.components.print.printDocument
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.data.user.UserStatus
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.error.asEmptyDataResult
import org.mr0btain.ticketman.error.getOrNull
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.response.InviteResponse
import org.mr0btain.ticketman.server.response.PendingUserResponse
import kotlin.uuid.Uuid

/**
 * The Users tab: staff accounts, account requests and invites, and PINs. The user list itself is the records
 * handler's, [onUsersChanged] tells it to reload
 * @param reloadOverview Account counts are on the overview
 * @param reloadSessions Deactivating, deleting or a new password logs someone out
 */
class AdminAccounts(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport,
    private val onUsersChanged: (Uuid?) -> Unit,
    private val reloadOverview: suspend () -> Unit,
    private val reloadSessions: suspend () -> Unit
) {
    private val users get() = remoteHandler.userRepo
    private val scope get() = support.scope

    private val _pending = MutableStateFlow<List<PendingUserResponse>>(emptyList())
    /** Requests and invites waiting, which badge the Users tab */
    val pending = _pending.asStateFlow()

    suspend fun reloadPending() {
        _pending.value = users.getPending()
    }

    /**
     * Adds or updates a user
     * @param password Only used for new users, existing ones change it through [resetPassword]
     * @param onSuccess Run once the server accepts it, used to close the dialog
     */
    fun saveUser(user: User, isNew: Boolean, password: String, onSuccess: () -> Unit) {
        scope.launch {
            val result = if (isNew) users.add(user.copy(password = password)).asEmptyDataResult() else users.update(user.withoutPassword())

            support.report(result, if (isNew) "Added ${user.username}" else "Saved ${user.username}") {
                onSuccess()
                onUsersChanged(user.identifier)
                reloadOverview()
            }
        }
    }

    /** Sets a new password for [user]. The server logs them out of their other devices */
    fun resetPassword(user: User, password: String, onSuccess: () -> Unit) {
        scope.launch {
            support.report(users.resetPassword(user.identifier, password), "Password changed for ${user.username}") {
                onSuccess()
                reloadSessions()
            }
        }
    }

    /**
     * Deactivating blocks logging in and ends their sessions, reactivating keeps the access level they had
     */
    fun setUserActive(user: User, active: Boolean) {
        val status = if (active) UserStatus.ACTIVE else UserStatus.DEACTIVATED
        val message = if (active) "Reactivated ${user.username}" else "Deactivated ${user.username}"

        scope.launch {
            support.report(users.update(user.withoutPassword().copy(status = status)), message) {
                onUsersChanged(user.identifier)
                reloadSessions()
                reloadOverview()
            }
        }
    }

    /** Deletes [user] for good, with their sessions and staff notes. Deactivating keeps their history instead */
    fun deleteUser(user: User) {
        scope.launch {
            support.report(users.remove(user.identifier), "Deleted ${user.username}") {
                onUsersChanged(null)
                reloadSessions()
                reloadOverview()
            }
        }
    }

    // ------------------------------------------ New accounts ------------------------------------------

    /** Lets a requested account in. They finish setting up on their first login */
    fun approve(user: User, accessLevel: AccessLevel, onSuccess: () -> Unit) {
        scope.launch {
            support.report(users.approve(user.identifier, accessLevel), "Approved ${user.username}") {
                onSuccess()
                reloadPending()
                onUsersChanged(user.identifier)
                reloadOverview()
            }
        }
    }

    /** Turns down a request or cancels an invite */
    fun reject(user: User) {
        scope.launch {
            support.report(users.reject(user.identifier), "Removed ${user.username}") {
                reloadPending()
                onUsersChanged(null)
                reloadOverview()
            }
        }
    }

    /**
     * Makes an invite
     * @param onCreated Gets the invite with its code, which is only shown this once
     */
    fun invite(request: InviteRequest, onCreated: (InviteResponse) -> Unit) {
        scope.launch {
            when (val result = users.invite(request)) {
                is TmResult.Success -> {
                    onCreated(result.data)
                    reloadPending()
                    onUsersChanged(result.data.user.identifier)
                    reloadOverview()
                    support.auditChanged()
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /** New code for an invite, the old one stops working */
    fun renewInvite(user: User, onCreated: (InviteResponse) -> Unit) {
        scope.launch {
            when (val result = users.renewInvite(user.identifier)) {
                is TmResult.Success -> {
                    onCreated(result.data)
                    reloadPending()
                    support.auditChanged()
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    // ------------------------------------------ PINs ------------------------------------------

    /** Unlocks a PIN locked by wrong tries, keeping the PIN */
    fun unlockPin(user: User) {
        scope.launch {
            support.report(users.unlockPin(user.identifier), "Unlocked ${user.username}'s PIN") {
                onUsersChanged(user.identifier)
            }
        }
    }

    /** Clears their PIN so they set a new one from their settings */
    fun resetPin(user: User) {
        scope.launch {
            support.report(users.resetPin(user.identifier), "Cleared ${user.username}'s PIN") {
                onUsersChanged(user.identifier)
            }
        }
    }

    // ------------------------------------------ Photo and ID card ------------------------------------------

    /** Sets [user]'s profile photo, one just uploaded, or takes it off with null */
    fun setPhoto(user: User, photo: StoredImage?) {
        scope.launch {
            support.report(users.setPhoto(user.identifier, photo).asEmptyDataResult(), if (photo == null) "Took ${user.username}'s photo off" else "Saved ${user.username}'s photo") {
                onUsersChanged(user.identifier)
            }
        }
    }

    /** Prints [user]'s ID card on the shop's default paper, making them a code first if they have none */
    fun printIdCard(user: User) {
        val onResult: (String?) -> Unit = { problem -> problem?.let { support.notify("Couldn't print the card: $it") } }

        scope.launch {
            val code = when (val existing = users.getBadge(user.identifier)) {
                is TmResult.Success -> existing.data.code
                is TmResult.Error -> {
                    if (!existing.error.isNotFound) return@launch onResult(existing.error.message)

                    when (val issued = users.issueBadge(user.identifier)) {
                        is TmResult.Success -> issued.data.code.also { onUsersChanged(user.identifier) }
                        is TmResult.Error -> return@launch onResult(issued.error.message)
                    }
                }
            }

            val shop = remoteHandler.adminRepo.getShopProfile() ?: ShopProfile()
            // A card without the photo beats no card, so a photo that won't load is left off
            val photo = user.photo?.let { image ->
                remoteHandler.imageRepo.bytes(image.identifier, ImageVariant.DISPLAY).getOrNull()
                    ?.let { PrintBlock.Photo(it, image.aspectRatio ?: 1f) }
            }

            onResult(printDocument(PrintTemplates.idCard(shop, user, code, photo), shop.defaultPaper))
        }
    }

    /** Gives [user] a new ID card code, so a lost card stops working. Print the card again after */
    fun newBadge(user: User) {
        scope.launch {
            support.report(users.issueBadge(user.identifier).asEmptyDataResult(), "New card code for ${user.username}, print their card again") {
                onUsersChanged(user.identifier)
            }
        }
    }

    fun clear() {
        _pending.value = emptyList()
    }
}
