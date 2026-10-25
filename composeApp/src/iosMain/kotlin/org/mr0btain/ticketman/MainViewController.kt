package org.mr0btain.ticketman

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.injection.initKoin
import org.mr0btain.ticketman.ui.AppWindow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityGuidedAccessStatusDidChangeNotification
import platform.UIKit.UIAccessibilityIsGuidedAccessEnabled
import platform.UIKit.UIAccessibilityRequestGuidedAccessSession
import platform.UIKit.UIViewController

// SwiftUI may ask for the view controller more than once, Koin only starts the first time
private var started = false

/** The whole app as a view controller, which iosApp's ContentView shows */
fun MainViewController(): UIViewController {
    if (!started) {
        initKoin()
        watchGuidedAccess()
        started = true
    }

    return ComposeUIViewController { App() }
}

/**
 * Kiosk mode's pinning is Guided Access here (triple click the side button), which only the person holding the device
 * can start, unless it's a supervised device set up for it. So pinned is reported while it's on, and left unknown
 * while it's off so the kiosk doesn't keep offering a pin button that can't work
 */
private fun watchGuidedAccess() {
    fun report() = AppWindow.setPinned(if (UIAccessibilityIsGuidedAccessEnabled()) true else null)

    report()
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIAccessibilityGuidedAccessStatusDidChangeNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue
    ) { _ -> report() }

    // Works on a supervised device with the app allowed to lock itself, and does nothing anywhere else
    MainScope().launch {
        AppWindow.pinRequests.collect { if (AppWindow.fullscreen.value) UIAccessibilityRequestGuidedAccessSession(true) { _ -> report() } }
    }
}
