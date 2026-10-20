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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.ui.kit.containers.TmScrollColumn
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.components.AccountPanel
import org.mr0btain.ticketman.ui.screens.settings.components.AppearanceSettings
import org.mr0btain.ticketman.ui.screens.settings.components.ChangePinDialog
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItem
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItemGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsGroup
import org.mr0btain.ticketman.ui.screens.settings.components.StationDialog
import ticketman.composeapp.generated.resources.*
import org.mr0btain.ticketman.components.data.local.UpdateInterval
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsHandler

/**
 * This device's settings and the logged in account. Saved as soon as they're changed, see [SettingsHandler]
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    onOpenGallery: (() -> Unit)? = null
) {
    val darkMode = viewModel.settings.darkMode.collectAsState()
    val palette = viewModel.settings.palette.collectAsState()
    val font = viewModel.settings.font.collectAsState()
    val reduceTransparency = viewModel.settings.reduceTransparency.collectAsState()
    val serverUrl = viewModel.settings.serverUrl.collectAsState()
    val rememberLogin = viewModel.settings.rememberLogin.collectAsState()
    val defaultTab = viewModel.settings.defaultTab.collectAsState()
    val pageSize = viewModel.settings.pageSize.collectAsState()
    val searchLimit = viewModel.settings.searchLimit.collectAsState()
    val updateInterval = viewModel.settings.updateInterval.collectAsState()
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
                SettingItem.Switch(
                    icon = if (rememberLogin.value) painterResource(Res.drawable.lock) else painterResource(Res.drawable.unlock),
                    label = "Remember login",
                    description = "Save username and password after logging in",
                    checked = rememberLogin.value,
                    onToggle = { viewModel.settings.toggleRememberLogin() }
                )
            )
        ),
        SettingItemGroup(
            label = "Server",
            icon = Res.drawable.server,
            items = listOf(
                SettingItem.Text(
                    icon = painterResource(Res.drawable.server),
                    label = "Server address",
                    description = "Where the server is, like tickets.myshop.com, or 192.168.1.20:8443 for one without a proxy. Uses HTTPS unless it starts with http://",
                    value = serverUrl.value,
                    onChange = { url ->
                        viewModel.settings.setServerUrl(url)
                        true
                    }
                ),
                SettingItem.Text(
                    icon = painterResource(Res.drawable.monitor),
                    label = "Device name",
                    description = "Name admins see for this device, used from the next login",
                    value = deviceName.value,
                    onChange = { name ->
                        viewModel.settings.setDeviceName(name)
                        true
                    }
                )
            )
        ),
        SettingItemGroup(
            label = "Records",
            icon = Res.drawable.list,
            items = listOf(
                SettingItem.Choice(
                    icon = painterResource(Res.drawable.home),
                    label = "Start tab",
                    description = "Records tab shown on start",
                    selected = tabNames.getOrElse(defaultTab.value) { "" },
                    options = tabNames,
                    onSelect = { name ->
                        tabNames.indexOf(name).takeIf { it >= 0 }?.let { viewModel.settings.setDefaultTab(it) }
                    }
                ),
                SettingItem.Choice(
                    icon = painterResource(Res.drawable.refresh_cw),
                    label = "Check for updates every",
                    description = "How often open lists, records, the counter queue and the admin screen pick up changes from other devices",
                    selected = updateInterval.value.label,
                    options = UpdateInterval.entries.map { it.label },
                    onSelect = { label ->
                        UpdateInterval.entries.firstOrNull { it.label == label }?.let { viewModel.settings.setUpdateInterval(it) }
                    }
                ),
                numberSetting(
                    icon = painterResource(Res.drawable.list),
                    label = "Page size",
                    description = "Number of items loaded per page",
                    value = pageSize.value,
                    range = SettingsHandler.PAGE_SIZE_RANGE,
                    onChange = viewModel.settings::setPageSize
                ),
                numberSetting(
                    icon = painterResource(Res.drawable.search),
                    label = "Search result limit",
                    description = "Max number of search results",
                    value = searchLimit.value,
                    range = SettingsHandler.SEARCH_LIMIT_RANGE,
                    onChange = viewModel.settings::setSearchLimit
                ),
                numberSetting(
                    icon = painterResource(Res.drawable.database),
                    label = "Cache size",
                    description = "Opened records kept so going back to them is quick",
                    value = cacheSize.value,
                    range = SettingsHandler.CACHE_SIZE_RANGE,
                    onChange = viewModel.settings::setCacheSize
                )
            )
        ),
        SettingItemGroup(
            label = "Account and station",
            icon = Res.drawable.key,
            items = listOf(
                SettingItem.Action(
                    icon = painterResource(Res.drawable.key),
                    label = "Change PIN",
                    description = when {
                        currentUser.value == null -> "Log in to change your PIN"
                        currentUser.value?.pinLocked == true -> "Your PIN is locked, ask an admin to unlock it"
                        currentUser.value?.hasPin == true -> "PIN used to sign in at counter stations"
                        else -> "You don't have a PIN yet, set one to use counter stations"
                    },
                    enabled = currentUser.value != null,
                    onClick = { showChangePin = true }
                ),
                SettingItem.Action(
                    icon = painterResource(Res.drawable.monitor),
                    label = "Counter station",
                    description = when (station.value?.status) {
                        null -> "Make this device a station staff can PIN in at"
                        StationStatus.PENDING -> "Waiting for an admin to approve \"${station.value?.name}\""
                        StationStatus.APPROVED -> "Approved as \"${station.value?.name}\""
                    },
                    onClick = { showStation = true }
                )
            )
        )
    )

    val apiMismatch = viewModel.session.apiMismatch.collectAsState()
    val connectionProblem = viewModel.session.connectionProblem.collectAsState()

    val account: @Composable () -> Unit = {
        AccountPanel(
            user = currentUser.value,
            onLogin = { viewModel.session.changeShowLogin(true) },
            onLogout = { viewModel.session.logout() },
            apiMismatch = apiMismatch.value,
            connectionProblem = connectionProblem.value
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

/** A typed whole number setting that refuses anything outside [range], which its description gives */
private fun numberSetting(
    icon: Painter,
    label: String,
    description: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) = SettingItem.Text(
    icon = icon,
    label = label,
    description = "$description (${range.first}-${range.last})",
    value = value.toString(),
    onChange = { text -> text.trim().toIntOrNull()?.takeIf { it in range }?.let { onChange(it); true } ?: false }
)

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
