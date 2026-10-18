package org.mr0btain.ticketman.components.debug

import kotlinx.browser.window

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class BuildConfigImpl actual constructor() : BuildConfig {
    // Web builds have no debug flag, so a page opened from this machine (the dev server) counts as debug
    actual override fun isDebug(): Boolean = window.location.hostname in setOf("localhost", "127.0.0.1")
}
