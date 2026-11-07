package org.mr0btain.ticketman.ui.screens.setup.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.request.InviteRequest
import org.mr0btain.ticketman.server.response.InviteResponse

/**
 * The first run setup: an admin on a new server is walked through the shop's details, money and tax, repair terms
 * and staff before anything else. Finishing or skipping it marks it done on the server
 * ([ShopProfile.setupDone]), so it's shown once per shop, not once per device
 * @param onShopChanged Called with the profile once it's saved, for the counter and everything reading it
 */
class SetupHandler(
    private val remoteHandler: RemoteHandler,
    private val scope: CoroutineScope,
    private val isAdmin: () -> Boolean,
    private val onShopChanged: (ShopProfile) -> Unit
) {
    private val _profile = MutableStateFlow<ShopProfile?>(null)
    /** The shop's profile as it was when setup started, null while it isn't needed */
    val profile = _profile.asStateFlow()

    /** Whether the setup wizard takes the app, for an admin on a shop that isn't set up */
    val needed: Boolean
        get() = _profile.value?.needsSetup == true

    /** Looks whether the shop needs setting up, after an admin logs in. Anyone else never sees it */
    fun check() {
        if (!isAdmin()) {
            _profile.value = null
            return
        }
        scope.launch {
            _profile.value = remoteHandler.adminRepo.getShopProfile()?.takeIf { it.needsSetup }
        }
    }

    /**
     * Saves what was filled in and marks setup done
     * @param onResult Gets null when it's saved, otherwise what went wrong
     */
    fun finish(profile: ShopProfile, onResult: (String?) -> Unit) {
        scope.launch {
            val saving = profile.copy(setupDone = true)
            when (val result = remoteHandler.adminRepo.updateShopProfile(saving)) {
                is TmResult.Success -> {
                    // Read back, since the server tidies it
                    val saved = remoteHandler.adminRepo.getShopProfile() ?: saving
                    _profile.value = null
                    onShopChanged(saved)
                    onResult(null)
                }

                is TmResult.Error -> onResult(result.error.message)
            }
        }
    }

    /** Leaves the shop as it is and doesn't ask again; it's all in Admin > Server and data */
    fun skip(onResult: (String?) -> Unit) {
        val profile = _profile.value ?: return onResult(null)
        finish(profile, onResult)
    }

    /**
     * Invites someone to the shop, their setup code shown once
     * @param onResult Gets the invite, or null and what went wrong
     */
    fun invite(request: InviteRequest, onResult: (InviteResponse?, String?) -> Unit) {
        scope.launch {
            when (val result = remoteHandler.userRepo.invite(request)) {
                is TmResult.Success -> onResult(result.data, null)
                is TmResult.Error -> onResult(null, result.error.message)
            }
        }
    }

    fun clear() {
        _profile.value = null
    }
}
