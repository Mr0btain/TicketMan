package org.mr0btain.ticketman

import platform.UIKit.UIDevice

/**
 * The device's model and system, like "iPad, iPadOS 18.2". iOS only gives apps the name someone gave the device
 * with a special entitlement, so this is what admins see until they rename it in Settings
 */
class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.let { "${it.model}, ${it.systemName} ${it.systemVersion}" }
}

actual fun getPlatform(): Platform = IOSPlatform()
