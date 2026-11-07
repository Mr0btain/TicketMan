package org.mr0btain.ticketman.ui

import org.mr0btain.uikit.rules.KitRules
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Keeps screens on the UI kit (Tm-UI-Kit, and TicketMan's own components in ui/kit). Colors, sizes, shapes and
 * controls belong in the kit so changing one there changes it everywhere. If this fails, use or add a kit component
 * instead of styling the screen directly. The rules are Tm-UI-Kit's KitRules, tested there
 */
class UiKitRulesTest {
    private val root = File("src/commonMain/kotlin/org/mr0btain/ticketman")

    /** Every UI file that isn't part of the kit */
    private val screenFiles: List<File> =
        (root.resolve("ui").walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/kit/") } +
            root.resolve("App.kt")).toList()

    @Test
    fun screensOnlyStyleThroughTheKit() {
        assertTrue(screenFiles.size > 20, "Expected to find the screens under ${root.absolutePath}")

        val problems = KitRules.checkFiles(root, screenFiles)

        assertTrue(problems.isEmpty(), "Screens styling things outside the ui kit:\n" + problems.joinToString("\n"))
    }
}
