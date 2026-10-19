package org.mr0btain.ticketman.components.injection

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.mr0btain.ticketman.components.debug.BuildConfigImpl
import org.mr0btain.ticketman.logging.AppLogger

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(
            platformModule, viewModelModule, dataModule
        )
    }.also {
        // After starting, since Android reads its debug flag from the context registered with koin
        AppLogger.debug = BuildConfigImpl().isDebug()
    }
