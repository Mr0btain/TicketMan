package org.mr0btain.ticketman.components.injection

import android.content.Context
import org.koin.core.context.GlobalContext
import org.mr0btain.ticketman.util.AppDir

// Only called from the koin data module, where the app has already registered its context
actual fun initAppDir(): AppDir {
    return AppDir(GlobalContext.get().get<Context>())
}
