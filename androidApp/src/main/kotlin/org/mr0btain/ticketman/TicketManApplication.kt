package org.mr0btain.ticketman

import android.app.Application
import android.content.Context
import org.koin.dsl.module
import org.mr0btain.ticketman.components.injection.initKoin

/**
 * Starts koin once per process, starting it from the activity would crash when the activity gets recreated
 */
class TicketManApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            // KSafe needs a context on android
            modules(module { single<Context> { this@TicketManApplication.applicationContext } })
        }
    }
}
