package org.mr0btain.ticketman.components.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What's been typed into a screen's open forms, held by its handler so it's still there after a visit to another
 * screen. Each slot works like `remember(keys)`: the same keys get the same value, other keys start it over. The
 * handler empties it when the form is finished with and on log out, since it can hold things like a device's passcode
 */
class FormMemory {
    private class Slot(val keys: List<Any?>, val value: Any)

    private val slots = mutableMapOf<String, Slot>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> keep(slot: String, keys: List<Any?>, make: () -> T): T =
        slots[slot]?.takeIf { it.keys == keys }?.value as T?
            ?: make().also { slots[slot] = Slot(keys, it) }

    fun clear() = slots.clear()
}

/** Where [rememberKept] keeps things, provided by screens whose handler holds a [FormMemory] */
val LocalFormMemory = staticCompositionLocalOf<FormMemory?> { null }

/**
 * `remember(keys)` that outlasts the screen, through [LocalFormMemory]. Without one, like in a render test, it's a plain remember
 * @param slot Names this value, unique among those in the same memory
 */
@Composable
fun <T : Any> rememberKept(slot: String, vararg keys: Any?, make: () -> T): T {
    val memory = LocalFormMemory.current

    return if (memory != null) memory.keep(slot, keys.toList(), make) else remember(*keys) { make() }
}
