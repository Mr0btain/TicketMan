package org.mr0btain.ticketman.components.data.user

import org.mr0btain.ticketman.components.data.CustomCache
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.User

class UserCache(maxSize: Int): CustomCache<UserCache.UserData>(maxSize) {
    fun put(user: User, notes: List<Note> = emptyList(), images: List<StoredImage> = emptyList()) =
        cache.put(user.identifier.toString(), UserData(user, notes, images))

    data class UserData(
        val user: User,
        val notes: List<Note>,
        val images: List<StoredImage>
    )
}