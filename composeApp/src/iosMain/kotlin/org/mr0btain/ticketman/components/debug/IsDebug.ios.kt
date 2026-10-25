package org.mr0btain.ticketman.components.debug

import kotlin.experimental.ExperimentalNativeApi

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class BuildConfigImpl actual constructor() : BuildConfig {
    // Xcode's Debug configuration builds the framework as a debug binary, Release doesn't
    @OptIn(ExperimentalNativeApi::class)
    actual override fun isDebug(): Boolean = Platform.isDebugBinary
}
