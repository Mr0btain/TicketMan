package org.mr0btain.ticketman.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where the desktop client and a server without DATA_PATH keep their files on each system. Only Linux has been run
 * for real, so these pin down what macOS and Windows should get
 */
class AppDirTest {
    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    @Test
    fun osNamesAreRecognised() {
        assertEquals(Platform.Windows, platformFromOsName("Windows 11"))
        assertEquals(Platform.Windows, platformFromOsName("Windows Server 2022"))
        assertEquals(Platform.MacOS, platformFromOsName("Mac OS X"))
        assertEquals(Platform.Linux, platformFromOsName("Linux"))
        assertEquals(Platform.Linux, platformFromOsName("FreeBSD"))
    }

    @Test
    fun linuxPrefersXdgDataHome() {
        assertEquals(
            "/data/me/TicketMan",
            appDataFolder(Platform.Linux, "TicketMan", env("XDG_DATA_HOME" to "/data/me/", "HOME" to "/home/me"))
        )
        assertEquals("/home/me/.local/share/TicketMan", appDataFolder(Platform.Linux, "TicketMan", env("HOME" to "/home/me")))
        assertEquals(
            "/home/me/.local/share/TicketMan",
            appDataFolder(Platform.Linux, "TicketMan", env("XDG_DATA_HOME" to " ", "HOME" to "/home/me"))
        )
    }

    @Test
    fun macUsesApplicationSupport() {
        assertEquals(
            "/Users/me/Library/Application Support/TicketMan",
            appDataFolder(Platform.MacOS, "TicketMan", env("HOME" to "/Users/me"))
        )
    }

    @Test
    fun windowsUsesAppData() {
        assertEquals(
            """C:\Users\me\AppData\Roaming\TicketMan""",
            appDataFolder(Platform.Windows, "TicketMan", env("APPDATA" to """C:\Users\me\AppData\Roaming\"""))
        )
    }

    @Test
    fun missingVariablesGiveNoFolder() {
        assertNull(appDataFolder(Platform.Linux, "TicketMan", env()))
        assertNull(appDataFolder(Platform.MacOS, "TicketMan", env("APPDATA" to "C:\\x")))
        assertNull(appDataFolder(Platform.Windows, "TicketMan", env("HOME" to "/home/me")))
        assertNull(appDataFolder(Platform.Android, "TicketMan", env("HOME" to "/home/me")))
    }
}
