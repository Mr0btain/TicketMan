package org.mr0btain.ticketman.components.injection

import org.koin.core.module.Module

// Namespace for KSafe so our keys don't collide with other KSafe apps on desktop and web
internal const val KSAFE_NAMESPACE = "ticketman"

/**
 * Platform specific dependencies, currently just the KSafe instance
 */
expect val platformModule: Module
