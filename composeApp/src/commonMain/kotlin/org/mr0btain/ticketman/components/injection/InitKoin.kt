package org.mr0btain.ticketman.components.injection

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.mr0btain.ticketman.components.debug.BuildConfigImpl
import org.mr0btain.applogger.AppLogger
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.components.logging.ClientLogs
import org.mr0btain.ticketman.util.AppDir

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(
            platformModule, viewModelModule, dataModule
        )
    }.also {
        // After starting, since Android reads its debug flag from the context registered with koin
        AppLogger.debug = BuildConfigImpl().isDebug()
        // Log files, as early as the folder and settings are known
        ClientLogs.start(it.koin.get<AppDir>(), it.koin.get<LocalStorage>())
    }
