package org.mr0btain.ticketman.components.debug

interface BuildConfig {
    fun isDebug(): Boolean
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class BuildConfigImpl() : BuildConfig {
    override fun isDebug(): Boolean
}
