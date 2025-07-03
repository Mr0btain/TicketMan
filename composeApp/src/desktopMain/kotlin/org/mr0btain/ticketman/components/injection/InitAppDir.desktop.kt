package org.mr0btain.ticketman.components.injection

import org.mr0btain.ticketman.util.AppDir

actual fun initAppDir(): AppDir {
    return AppDir()
}