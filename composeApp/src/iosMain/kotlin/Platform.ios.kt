import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = "IOS" // UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()