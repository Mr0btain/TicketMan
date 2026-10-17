package org.mr0btain.ticketman.components.injection

import android.content.Context
import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import org.koin.dsl.module

// Context is registered by the android app when it starts koin
actual val platformModule = module {
    single { KSafe(get<Context>(), config = KSafeConfig(appNamespace = KSAFE_NAMESPACE)) }
}
