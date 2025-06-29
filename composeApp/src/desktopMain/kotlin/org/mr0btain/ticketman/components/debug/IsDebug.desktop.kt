package org.mr0btain.ticketman.components.debug

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class BuildConfigImpl actual constructor() : BuildConfig {
    override fun isDebug(): Boolean {
        val debugSystemProperty = System.getProperty("syncSphereDebug")
        return debugSystemProperty?.toBoolean() ?: false
    }
}