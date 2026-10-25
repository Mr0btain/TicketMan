package org.mr0btain.ticketman.data.user

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.MainItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A user of the app. The server stores [password] as a hash and blanks it before sending a user to a client,
 * clients only fill it in when creating a user.
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class User(
    override val identifier: Uuid = Uuid.generateV7(),
    val username: String = "",
    val password: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val accessLevel: AccessLevel = AccessLevel.RESTRICTED,
    val status: UserStatus = UserStatus.ACTIVE,
    /** Approved but hasn't finished first login setup, can't do anything else until they do */
    val needsSetup: Boolean = false,
    /** Has a PIN for stations, the PIN itself never leaves the server */
    val hasPin: Boolean = false,
    /** Too many wrong PINs, an admin has to unlock it */
    val pinLocked: Boolean = false,
    /** Their profile photo, set by an admin or themselves. Not changed by an update, see `/api/user/set/photo` */
    val photo: StoredImage? = null,
    /** Has an ID card code, the code itself only goes to admins */
    val hasBadge: Boolean = false,
    /** The location they usually work at, which their lists start on. Null for the main location */
    val homeLocation: Uuid? = null
): MainItem() {
    /** Active, set up, with a real access level. The only kind of user that can do anything */
    fun canAccess(minimum: AccessLevel): Boolean =
        status == UserStatus.ACTIVE && !needsSetup && accessLevel.meets(minimum)

    /** Copy that's safe to send over the network */
    fun withoutPassword(): User = copy(password = "")
}
