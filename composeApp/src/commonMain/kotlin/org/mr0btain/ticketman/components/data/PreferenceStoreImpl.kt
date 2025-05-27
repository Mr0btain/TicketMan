package org.mr0btain.ticketman.components.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferenceStoreImpl(
    private val datastore : DataStore<Preferences>
) : PreferenceStore{

    // Retrieves settings
    override fun getSettings() : Flow<UserSettings> =
        datastore.data.map{preference ->
            val darkMode = preference[PreferenceStore.DARK_MODE] ?: false
            val serverAddress = preference[PreferenceStore.SERVER_ADDRESS] ?: ""
            val scrollOnRight = preference[PreferenceStore.SCROLL_ON_RIGHT] ?: true
            val scrollOnTop = preference[PreferenceStore.SCROLL_ON_TOP] ?: true
            UserSettings(
                darkMode = darkMode,
                serverAddress = serverAddress,
                verticalScrollRight = scrollOnRight,
                horizontalScrollTop = scrollOnTop
            )
        }

    // Saves settings
    override suspend fun saveSettings(settings : UserSettings){
        datastore.edit{preference ->
            preference[PreferenceStore.DARK_MODE] = settings.darkMode
            preference[PreferenceStore.SERVER_ADDRESS] = settings.serverAddress
            preference[PreferenceStore.SCROLL_ON_RIGHT] = settings.verticalScrollRight
            preference[PreferenceStore.SCROLL_ON_TOP] = settings.horizontalScrollTop
        }
    }
}