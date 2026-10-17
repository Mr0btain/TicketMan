package org.mr0btain.ticketman.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Keeps screens on the ui kit. Colors, sizes, shapes and controls belong in ui/kit so changing one there
 * changes it everywhere. If this fails, use or add a kit component instead of styling the screen directly
 */
class UiKitRulesTest {
    private val root = File("src/commonMain/kotlin/org/mr0btain/ticketman")

    /** Every UI file that isn't part of the kit */
    private val screenFiles: List<File> =
        (root.resolve("ui").walkTopDown().filter { it.extension == "kt" && !it.path.contains("/ui/kit/") } +
            root.resolve("App.kt")).toList()

    /** Material pieces screens may still use directly, the kit wraps everything else */
    private val allowedMaterial = setOf("Text", "Icon", "SnackbarHostState")

    private val bannedCode = listOf(
        "Color(0x" to "a hard coded color, use TmTheme.colors",
        "fontSize =" to "a hard coded text size, use TmTheme.typography",
        "RoundedCornerShape(" to "a hand made shape, use TmTheme.shapes",
        "MaterialTheme." to "Material's theme, use TmTheme"
    )

    @Test
    fun screensOnlyStyleThroughTheKit() {
        assertTrue(screenFiles.size > 20, "Expected to find the screens under ${root.absolutePath}")

        val problems = screenFiles.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val where = "${file.relativeTo(root)}:${index + 1}"

                val material = Regex("""^import androidx\.compose\.material3\.(\w+|\*)$""").find(line.trim())?.groupValues?.get(1)
                when {
                    material != null && material !in allowedMaterial -> "$where imports Material's $material, use the kit's version"
                    line.trim().startsWith("import") -> null
                    else -> bannedCode.firstOrNull { (code, _) -> code in line }?.let { (_, why) -> "$where has $why" }
                }
            }
        }

        assertTrue(problems.isEmpty(), "Screens styling things outside the ui kit:\n" + problems.joinToString("\n"))
    }
}
