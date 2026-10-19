package org.mr0btain.ticketman

interface Platform {
    /** What this device is called, as an admin would recognise it: the computer's name, the phone model */
    val name: String
}

expect fun getPlatform(): Platform
