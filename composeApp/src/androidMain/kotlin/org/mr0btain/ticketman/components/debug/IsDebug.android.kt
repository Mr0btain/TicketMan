package org.mr0btain.ticketman.components.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import org.koin.core.context.GlobalContext

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class BuildConfigImpl actual constructor() : BuildConfig {
    // Debug builds are debuggable, release builds aren't. The app registers its context with koin on start
    actual override fun isDebug(): Boolean {
        val context = GlobalContext.getOrNull()?.getOrNull<Context>() ?: return false
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
