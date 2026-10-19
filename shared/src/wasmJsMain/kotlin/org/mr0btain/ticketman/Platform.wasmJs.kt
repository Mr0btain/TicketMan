package org.mr0btain.ticketman

class WasmPlatform: Platform {
    override val name: String = "Web browser"
}

actual fun getPlatform(): Platform = WasmPlatform()
