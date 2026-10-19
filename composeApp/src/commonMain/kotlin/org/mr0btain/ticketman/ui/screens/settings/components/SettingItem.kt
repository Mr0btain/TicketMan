package org.mr0btain.ticketman.ui.screens.settings.components

import androidx.compose.ui.graphics.painter.Painter

// TODO: We probably shouldn't use enabled here, but for now its just here as a placeholder i guess

/**
 * @param options What a [SettingType.CHOICE] offers. [onClick] gets the picked one
 */
data class SettingItem<T>(
    val icon: Painter,
    val description: String,
    val label: String,
    val enabled: Boolean,
    val initialState: T,
    val onClick: ((T) -> Boolean)? = null,
    val type: SettingType,
    val options: List<T> = emptyList()
) {
    companion object {
        inline fun <reified T> SettingItem<*>.runIfType(block: (SettingItem<T>) -> Unit) {
            (this as? SettingItem<T>)?.let(block)
        }
    }
}
