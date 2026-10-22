package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

/** A user's ID card code, see `BadgeCode` */
@Serializable
data class BadgeResponse(val code: String)
