package org.mr0btain.ticketman.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.ui.kit.containers.TmScrollColumn
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.components.AccountPanel
import org.mr0btain.ticketman.ui.screens.settings.components.AppearanceSettings
import org.mr0btain.ticketman.ui.screens.settings.components.ChangePinDialog
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItem
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItemGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SettingType
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsGroup
import org.mr0btain.ticketman.ui.screens.settings.components.StationDialog
import ticketman.composeapp.generated.resources.*
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsHandler

/**
 * This device's settings and the logged in account. Saved as soon as they're changed, see [SettingsHandler]
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    scope: CoroutineScope,
    onOpenGallery: (() -> Unit)? = null
) {
    val TAG = "SettingsScreen"
    val CHECK_URL_TAG = "$TAG:CheckUrl"

    val darkMode = viewModel.settings.darkMode.collectAsState()
    val palette = viewModel.settings.palette.collectAsState()
    val font = viewModel.settings.font.collectAsState()
    val reduceTransparency = viewModel.settings.reduceTransparency.collectAsState()
    val serverUrl = viewModel.settings.serverUrl.collectAsState()
    val rememberLogin = viewModel.settings.rememberLogin.collectAsState()
    val defaultTab = viewModel.settings.defaultTab.collectAsState()
    val pageSize = viewModel.settings.pageSize.collectAsState()
    val searchLimit = viewModel.settings.searchLimit.collectAsState()
    val cacheSize = viewModel.settings.cacheSize.collectAsState()
    val deviceName = viewModel.settings.deviceName.collectAsState()
    val station = viewModel.session.station.collectAsState()
    val currentUser = viewModel.session.currentUser.collectAsState()

    var showChangePin by remember { mutableStateOf(false) }
    var showStation by remember { mutableStateOf(false) }

    ChangePinDialog(
        isVisible = showChangePin,
        onDismiss = { showChangePin = false },
        onSave = { password, pin, onResult -> viewModel.session.changePin(password, pin, onResult) }
    )

    StationDialog(
        isVisible = showStation,
        station = station.value,
        defaultName = deviceName.value,
        kioskActive = viewModel.kiosk.active.collectAsState().value,
        onResumeKiosk = { viewModel.kiosk.resume() },
        onRefresh = { viewModel.session.refreshStation() },
        onDismiss = { showStation = false },
        onRegister = { name, onResult -> viewModel.session.registerStation(name, onResult) }
    )

    // Names of the main screen tabs, in tab index order
    val tabNames = listOf("Customers", "Tickets", "Inventory")

    val settingsGroups = listOf(
        SettingItemGroup(
            label = "Login",
            icon = Res.drawable.lock,
            items = listOf(
                SettingItem(
                    icon = if (rememberLogin.value) painterResource(Res.drawable.lock) else painterResource(Res.drawable.unlock),
                    description = "Save username and password after logging in",
                    label = "Remember login",
                    enabled = true,
                    initialState = rememberLogin.value,
                    onClick = {
                        viewModel.settings.toggleRememberLogin()
                        true
                    },
                    type = SettingType.SWITCH
                )
            )
        ),
        SettingItemGroup(
            label = "Server",
            icon = Res.drawable.server,
            items = listOf(
                SettingItem(
                    icon = painterResource(Res.drawable.server),
                    description = "Where the server is, like 192.168.1.20:8443. Uses HTTPS unless it starts with http://",
                    label = "Server address",
                    enabled = true,
                    initialState = serverUrl.value,
                    onClick = { url ->
                        try {
                            viewModel.settings.setServerUrl(url as? String ?: "")
                            true
                        } catch (ex: Exception) {
                            AppLogger.e(CHECK_URL_TAG, SimpleResult.FAIL, ex)
                            false
                        }
                    },
                    type = SettingType.TEXT
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.monitor),
                    description = "Name admins see for this device, used from the next login",
                    label = "Device name",
                    enabled = true,
                    initialState = deviceName.value,
                    onClick = { name ->
                        viewModel.settings.setDeviceName(name as? String ?: "")
                        true
                    },
                    type = SettingType.TEXT
                )
            )
        ),
        SettingItemGroup(
            label = "Records",
            icon = Res.drawable.list,
            items = listOf(
                SettingItem(
                    icon = painterResource(Res.drawable.home),
                    description = "Records tab shown on start",
                    label = "Start tab",
                    enabled = true,
                    initialState = tabNames.getOrElse(defaultTab.value) { "" },
                    options = tabNames,
                    onClick = { name ->
                        tabNames.indexOf(name).takeIf { it >= 0 }?.let { viewModel.settings.setDefaultTab(it); true } ?: false
                    },
                    type = SettingType.CHOICE
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.list),
                    description = "Number of items loaded per page " +
                            "(${SettingsHandler.PAGE_SIZE_RANGE.first}-${SettingsHandler.PAGE_SIZE_RANGE.last})",
                    label = "Page size",
                    enabled = true,
                    initialState = pageSize.value.toString(),
                    onClick = { value ->
                        (value as? String)?.trim()?.toIntOrNull()
                            ?.takeIf { it in SettingsHandler.PAGE_SIZE_RANGE }
                            ?.let { viewModel.settings.setPageSize(it); true } ?: false
                    },
                    type = SettingType.TEXT
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.search),
                    description = "Max number of search results " +
                            "(${SettingsHandler.SEARCH_LIMIT_RANGE.first}-${SettingsHandler.SEARCH_LIMIT_RANGE.last})",
                    label = "Search result limit",
                    enabled = true,
                    initialState = searchLimit.value.toString(),
                    onClick = { value ->
                        (value as? String)?.trim()?.toIntOrNull()
                            ?.takeIf { it in SettingsHandler.SEARCH_LIMIT_RANGE }
                            ?.let { viewModel.settings.setSearchLimit(it); true } ?: false
                    },
                    type = SettingType.TEXT
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.database),
                    description = "Opened records kept so going back to them is quick " +
                            "(${SettingsHandler.CACHE_SIZE_RANGE.first}-${SettingsHandler.CACHE_SIZE_RANGE.last})",
                    label = "Cache size",
                    enabled = true,
                    initialState = cacheSize.value.toString(),
                    onClick = { value ->
                        (value as? String)?.trim()?.toIntOrNull()
                            ?.takeIf { it in SettingsHandler.CACHE_SIZE_RANGE }
                            ?.let { viewModel.settings.setCacheSize(it); true } ?: false
                    },
                    type = SettingType.TEXT
                )
            )
        ),
        SettingItemGroup(
            label = "Account and station",
            icon = Res.drawable.key,
            items = listOf(
                SettingItem(
                    icon = painterResource(Res.drawable.key),
                    description = when {
                        currentUser.value == null -> "Log in to change your PIN"
                        currentUser.value?.pinLocked == true -> "Your PIN is locked, ask an admin to unlock it"
                        currentUser.value?.hasPin == true -> "PIN used to sign in at counter stations"
                        else -> "You don't have a PIN yet, set one to use counter stations"
                    },
                    label = "Change PIN",
                    enabled = currentUser.value != null,
                    initialState = "",
                    onClick = {
                        if (currentUser.value != null) showChangePin = true
                        true
                    },
                    type = SettingType.CLICKABLE_TEXT
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.monitor),
                    description = when (station.value?.status) {
                        null -> "Make this device a station staff can PIN in at"
                        StationStatus.PENDING -> "Waiting for an admin to approve \"${station.value?.name}\""
                        StationStatus.APPROVED -> "Approved as \"${station.value?.name}\""
                    },
                    label = "Counter station",
                    enabled = true,
                    initialState = "",
                    onClick = {
                        showStation = true
                        true
                    },
                    type = SettingType.CLICKABLE_TEXT
                )
            )
        )
    )

    val apiMismatch = viewModel.session.apiMismatch.collectAsState()

    val account: @Composable () -> Unit = {
        AccountPanel(
            user = currentUser.value,
            onLogin = { viewModel.session.changeShowLogin(true) },
            onLogout = { viewModel.session.logout() },
            apiMismatch = apiMismatch.value
        )
    }

    val appearance: @Composable () -> Unit = {
        AppearanceSettings(
            palette = palette.value,
            onPaletteChange = viewModel.settings::setPalette,
            darkTheme = darkMode.value,
            onDarkThemeChange = { dark -> if (dark != darkMode.value) viewModel.settings.toggleDarkMode() },
            font = font.value,
            onFontChange = viewModel.settings::setFont,
            reduceTransparency = reduceTransparency.value,
            onReduceTransparencyChange = { viewModel.settings.toggleReduceTransparency() },
            onOpenGallery = onOpenGallery
        )
    }

    SettingsLayout(
        modifier = modifier,
        first = {
            account()
            appearance()
        },
        second = {
            settingsGroups.forEach { group -> SettingsGroup(settingItemGroup = group) }
        }
    )
}

/** Wider than this, settings sit in two columns */
private val TWO_COLUMN_WIDTH = 1000.dp

/**
 * Settings in two columns side by side on wide windows, [first] then [second] in one column on narrow ones
 */
@Composable
fun SettingsLayout(
    modifier: Modifier = Modifier,
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit
) {
    val spacing = TmTheme.spacing

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= TWO_COLUMN_WIDTH

        TmScrollColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.md), content = first)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.md), content = second)
                }
            } else {
                first()
                second()
            }
        }
    }
}
