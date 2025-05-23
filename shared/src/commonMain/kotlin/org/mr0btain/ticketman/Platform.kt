package org.mr0btain.ticketman

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform