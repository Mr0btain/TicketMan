package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.inventory.AccessLevel

/**
 * Someone asking for an account from the login screen. It waits for an admin to approve it
 */
@Serializable
data class AccountRequest(
    val username: String,
    val password: String,
    val name: String = "",
    val email: String = "",
    val phone: String = ""
)

/** Admin making an account for someone, they finish it with the setup code */
@Serializable
data class InviteRequest(
    val username: String,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED
)

@Serializable
data class ApproveRequest(
    val accessLevel: AccessLevel
)

/**
 * First login setup for an approved request, the user is already logged in
 * @param password Optional new password, blank keeps the one they requested with
 */
@Serializable
data class AccountSetupRequest(
    val name: String,
    val email: String = "",
    val phone: String = "",
    val password: String = ""
)

/**
 * Finishing an invite. [setupToken] comes from logging in with the setup code
 */
@Serializable
data class InviteSetupRequest(
    val setupToken: String,
    val password: String,
    val name: String,
    val email: String = "",
    val phone: String = "",
    val deviceId: String = "",
    val deviceName: String = ""
)
