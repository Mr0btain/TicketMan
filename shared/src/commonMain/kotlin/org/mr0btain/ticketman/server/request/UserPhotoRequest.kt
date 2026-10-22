package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.image.StoredImage

/** A user's profile photo: one just uploaded, the one they have, or null to take it off */
@Serializable
data class UserPhotoRequest(val photo: StoredImage? = null)
