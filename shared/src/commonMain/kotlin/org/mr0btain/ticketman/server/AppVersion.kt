package org.mr0btain.ticketman.server

import org.mr0btain.ticketman.BuildInfo

/** The version this build was made as, `ticketman.version` in gradle.properties */
const val APP_VERSION = BuildInfo.VERSION

/** Header the server answers `/api/ping` with, holding its [APP_VERSION] */
const val APP_VERSION_HEADER = "X-TicketMan-Version"

/**
 * A release version, `major.minor.patch`. Anything after a `-` or `+` (`0.9.0-rc1`) is left off, so a release
 * candidate counts as the release it's for
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** This build's version */
        val current: AppVersion = parse(APP_VERSION) ?: AppVersion(0, 0, 0)

        /** [text] as a version, null when it isn't one */
        fun parse(text: String?): AppVersion? {
            val parts = text?.trim()?.substringBefore('-')?.substringBefore('+')?.split('.') ?: return null
            if (parts.size != 3) return null
            val numbers = parts.map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }

            return AppVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}

/** How far apart two versions are, see [VersionComparison] */
enum class VersionGap {
    /** The same release, or a patch apart */
    NONE,

    /** A minor version apart: they still work together, but the older one should be updated */
    MINOR,

    /**
     * A major version apart, with breaking changes between them, so they can't be used together. Before 1.0 a minor
     * version can break things too, so 0.8 and 0.9 are this far apart
     */
    MAJOR
}

/** The versions of a client and the server it reached, how far apart they are and which is behind */
data class VersionComparison(val client: AppVersion, val server: AppVersion) {
    val gap: VersionGap = when {
        client.major != server.major -> VersionGap.MAJOR
        client.minor != server.minor -> if (client.major == 0) VersionGap.MAJOR else VersionGap.MINOR
        else -> VersionGap.NONE
    }

    /** Whether the server is the older one, so it's the one to update. Otherwise it's the client */
    val serverBehind: Boolean
        get() = server < client

    /** Whether they can't be used together */
    val breaking: Boolean
        get() = gap == VersionGap.MAJOR
}
