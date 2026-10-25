package org.mr0btain.ticketman.components.injection

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import org.koin.dsl.module

// KSafe keeps its keys in the iOS keychain
actual val platformModule = module {
    single { KSafe(config = KSafeConfig(appNamespace = KSAFE_NAMESPACE)) }
}
