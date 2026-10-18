package org.mr0btain.ticketman.components.network

import kotlinx.browser.window

actual fun defaultServerAddress(): String = window.location.origin
