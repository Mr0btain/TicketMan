package org.mr0btain.ticketman.components.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow

interface PreferenceStore{

    fun getSettings() : Flow<UserSettings>

    suspend fun saveSettings(settings : UserSettings)

    companion object{
        //creating the Preferences.Key
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SERVER_ADDRESS = stringPreferencesKey("server_address")
        val SCROLL_ON_RIGHT = booleanPreferencesKey("scroll_on_right")
        val SCROLL_ON_TOP = booleanPreferencesKey("scroll_on_top")
    }

}
/*
    Boolean → booleanPreferenceKey()

    Int → intPreferenceKey()

    Long → longPreferenceKey()

    Float → floatPreferenceKey()

    String → stringPreferenceKey()

    Set → stringSetPreferenceKey()
 */