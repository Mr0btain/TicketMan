package org.mr0btain.ticketman.ui.screens.settings.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.ui.kit.theme.TmFont
import org.mr0btain.ticketman.ui.kit.theme.TmFonts
import org.mr0btain.ticketman.ui.kit.theme.TmPalette
import org.mr0btain.ticketman.ui.kit.theme.TmPalettes

/**
 * The app's saved settings: appearance, the server, and list and search sizes. Each change is saved to
 * [localStorage] right away
 * @param onServerUrlChanged Connects to the new server
 * @param onPageSizeChanged Reloads lists with the new size
 */
class SettingsHandler(
    private val localStorage: LocalStorage,
    private val scope: CoroutineScope,
    private val onServerUrlChanged: (String) -> Unit,
    private val onPageSizeChanged: (Int) -> Unit
) {
    private val _darkMode: MutableStateFlow<Boolean> = MutableStateFlow(LocalStorage.DEFAULT_DARK_MODE)
    val darkMode = _darkMode.asStateFlow()

    private val _palette: MutableStateFlow<TmPalette> = MutableStateFlow(TmPalettes.default)
    val palette = _palette.asStateFlow()

    private val _font: MutableStateFlow<TmFont> = MutableStateFlow(TmFonts.default)
    val font = _font.asStateFlow()

    private val _reduceTransparency: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val reduceTransparency = _reduceTransparency.asStateFlow()

    private val _serverUrl: MutableStateFlow<String> = MutableStateFlow("")
    val serverUrl = _serverUrl.asStateFlow()

    private val _rememberLogin: MutableStateFlow<Boolean> = MutableStateFlow(LocalStorage.DEFAULT_REMEMBER_LOGIN)
    val rememberLogin = _rememberLogin.asStateFlow()

    private val _defaultTab: MutableStateFlow<Int> = MutableStateFlow(LocalStorage.DEFAULT_TAB)
    val defaultTab = _defaultTab.asStateFlow()

    private val _pageSize: MutableStateFlow<Int> = MutableStateFlow(LocalStorage.DEFAULT_PAGE_SIZE)
    val pageSize = _pageSize.asStateFlow()

    private val _searchLimit: MutableStateFlow<Int> = MutableStateFlow(LocalStorage.DEFAULT_SEARCH_LIMIT)
    val searchLimit = _searchLimit.asStateFlow()

    private val _cacheSize: MutableStateFlow<Int> = MutableStateFlow(LocalStorage.DEFAULT_CACHE_SIZE)
    val cacheSize = _cacheSize.asStateFlow()

    // Name shown to admins in the session list, sent at login
    private val _deviceName: MutableStateFlow<String> = MutableStateFlow(localStorage.deviceName)
    val deviceName = _deviceName.asStateFlow()

    /** Pulls everything saved, called once on start */
    fun load() {
        _darkMode.value = localStorage.darkMode
        _palette.value = TmPalettes.byId(localStorage.themeId)
        _font.value = TmFonts.byId(localStorage.fontId)
        _reduceTransparency.value = localStorage.reduceTransparency
        _serverUrl.value = localStorage.serverUrl
        _rememberLogin.value = localStorage.rememberLogin
        _defaultTab.value = localStorage.defaultTab
        _pageSize.value = localStorage.pageSize
        _searchLimit.value = localStorage.searchLimit
        _cacheSize.value = localStorage.cacheSize
    }

    /** Takes effect on the next login */
    fun setDeviceName(name: String) {
        localStorage.deviceName = name
        _deviceName.value = localStorage.deviceName
    }

    fun toggleDarkMode() {
        val value = !_darkMode.value
        _darkMode.value = value
        localStorage.darkMode = value
    }

    fun setPalette(palette: TmPalette) {
        _palette.value = palette
        localStorage.themeId = palette.id
    }

    fun setFont(font: TmFont) {
        _font.value = font
        localStorage.fontId = font.id
    }

    fun toggleReduceTransparency() {
        val value = !_reduceTransparency.value
        _reduceTransparency.value = value
        localStorage.reduceTransparency = value
    }

    fun setServerUrl(url: String) {
        _serverUrl.value = url
        localStorage.serverUrl = url
        onServerUrlChanged(url)
    }

    fun toggleRememberLogin() {
        val value = !_rememberLogin.value
        _rememberLogin.value = value
        localStorage.rememberLogin = value

        // Forget anything we've already stored
        if (!value) {
            scope.launch { localStorage.clearCredentials() }
        }
    }

    /** Sets which main tab is shown on start, wraps around past the last tab */
    fun setDefaultTab(index: Int) {
        val value = index.mod(MAIN_TAB_COUNT)
        _defaultTab.value = value
        localStorage.defaultTab = value
    }

    fun setPageSize(size: Int) {
        val value = size.coerceIn(PAGE_SIZE_RANGE)
        _pageSize.value = value
        localStorage.pageSize = value
        onPageSizeChanged(value)
    }

    fun setSearchLimit(limit: Int) {
        val value = limit.coerceIn(SEARCH_LIMIT_RANGE)
        _searchLimit.value = value
        localStorage.searchLimit = value
    }

    /** Caches are only built on start, so this applies on next start */
    fun setCacheSize(size: Int) {
        val value = size.coerceIn(CACHE_SIZE_RANGE)
        _cacheSize.value = value
        localStorage.cacheSize = value
    }

    companion object {
        const val MAIN_TAB_COUNT = 3
        val PAGE_SIZE_RANGE = 5..100
        val SEARCH_LIMIT_RANGE = 1..100
        val CACHE_SIZE_RANGE = 1..200
    }
}
