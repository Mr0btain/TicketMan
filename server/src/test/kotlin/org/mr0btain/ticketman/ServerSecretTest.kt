package org.mr0btain.ticketman

import org.mr0btain.ticketman.components.service.ServerSecret
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.*

/**
 * Tokens are signed with `SECRET`, or one the server makes and keeps when it isn't set
 */
@OptIn(ExperimentalPathApi::class)
class ServerSecretTest {
    private val dataDir = createTestDataDir()
    private val otherDir = createTestDataDir()
    private val path = dataDir.invariantSeparatorsPathString

    @AfterTest
    fun cleanup() {
        dataDir.deleteRecursively()
        otherDir.deleteRecursively()
    }

    @Test
    fun aSetSecretIsUsedAsIs() {
        assertEquals("from-the-environment", ServerSecret.resolve("from-the-environment", path))
        assertFalse(ServerSecret.file(path).exists())
    }

    @Test
    fun withoutOneTheServerMakesOneAndKeepsIt() {
        val made = ServerSecret.resolve("", path)

        assertTrue(made.length >= 64)
        assertTrue(ServerSecret.file(path).isFile)
        assertEquals(made, ServerSecret.resolve("", path), "A restart has to sign with the same secret")
        assertNotEquals(made, ServerSecret.resolve("", otherDir.invariantSeparatorsPathString), "Each server makes its own")
    }
}
