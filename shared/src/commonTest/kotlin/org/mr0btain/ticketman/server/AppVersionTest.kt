package org.mr0btain.ticketman.server

import kotlin.test.*

/**
 * Versions are read as major.minor.patch, and a major apart (a minor before 1.0) can't work together
 */
class AppVersionTest {
    private fun compare(client: String, server: String) =
        VersionComparison(AppVersion.parse(client)!!, AppVersion.parse(server)!!)

    @Test
    fun versionsAreRead() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
        assertEquals(AppVersion(0, 9, 0), AppVersion.parse(" 0.9.0-rc1 "))
        assertEquals(AppVersion(2, 0, 1), AppVersion.parse("2.0.1+build5"))
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("1.x.0"))
        assertNull(AppVersion.parse("1.-2.0"))
    }

    @Test
    fun thisBuildHasAVersion() {
        assertEquals(AppVersion.parse(APP_VERSION), AppVersion.current)
    }

    @Test
    fun aPatchApartIsFine() {
        assertEquals(VersionGap.NONE, compare("1.4.0", "1.4.0").gap)
        assertEquals(VersionGap.NONE, compare("1.4.0", "1.4.7").gap)
        assertEquals(VersionGap.NONE, compare("0.9.1", "0.9.0").gap)
    }

    @Test
    fun aMinorApartAfterOnePointZeroStillWorks() {
        val comparison = compare("1.2.0", "1.5.3")
        assertEquals(VersionGap.MINOR, comparison.gap)
        assertFalse(comparison.breaking)
        assertFalse(comparison.serverBehind)
        assertTrue(compare("1.5.0", "1.2.0").serverBehind)
    }

    @Test
    fun aMajorApartCantWorkTogether() {
        val comparison = compare("1.0.0", "2.0.0")
        assertEquals(VersionGap.MAJOR, comparison.gap)
        assertTrue(comparison.breaking)
        assertFalse(comparison.serverBehind)
        assertTrue(compare("2.1.0", "1.9.9").serverBehind)
        assertTrue(compare("0.9.0", "1.0.0").breaking)
    }

    @Test
    fun beforeOnePointZeroAMinorApartCantWorkTogether() {
        assertTrue(compare("0.8.0", "0.9.0").breaking)
        assertTrue(compare("0.9.0", "0.8.2").serverBehind)
    }
}
