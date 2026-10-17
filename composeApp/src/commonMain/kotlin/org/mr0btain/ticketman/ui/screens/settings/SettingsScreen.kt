package org.mr0btain.ticketman.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.data.station.StationStatus
import org.mr0btain.ticketman.ui.screens.settings.components.ChangePinDialog
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItem
import org.mr0btain.ticketman.ui.screens.settings.components.StationDialog
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItemGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SettingType
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsGroup
import ticketman.composeapp.generated.resources.*

// For now, I think well be fine with using this on every screen size/ platform as it should fit just fine
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    scope: CoroutineScope
) {
    val TAG = "SettingsScreen"
    val CHECK_URL_TAG = "$TAG:CheckUrl"

    val darkMode = viewModel.darkMode.collectAsState()
    val serverUrl = viewModel.serverUrl.collectAsState()
    val rememberLogin = viewModel.rememberLogin.collectAsState()
    val defaultTab = viewModel.defaultTab.collectAsState()
    val pageSize = viewModel.pageSize.collectAsState()
    val searchLimit = viewModel.searchLimit.collectAsState()
    val cacheSize = viewModel.cacheSize.collectAsState()
    val deviceName = viewModel.deviceName.collectAsState()
    val station = viewModel.station.collectAsState()
    val currentUser = viewModel.currentUser.collectAsState()

    var showChangePin by remember { mutableStateOf(false) }
    var showStation by remember { mutableStateOf(false) }

    ChangePinDialog(
        isVisible = showChangePin,
        onDismiss = { showChangePin = false },
        onSave = { password, pin, onResult -> viewModel.changePin(password, pin, onResult) }
    )

    StationDialog(
        isVisible = showStation,
        station = station.value,
        defaultName = deviceName.value,
        kioskActive = viewModel.kiosk.active.collectAsState().value,
        onResumeKiosk = { viewModel.kiosk.resume() },
        onRefresh = { viewModel.refreshStation() },
        onDismiss = { showStation = false },
        onRegister = { name, onResult -> viewModel.registerStation(name, onResult) }
    )

    // Names of the main screen tabs, in tab index order
    val tabNames = listOf("Customers", "Tickets", "Inventory")

    val textStyle = MaterialTheme.typography.bodyMedium
    val childTextStyle = MaterialTheme.typography.bodySmall

    val settingsGroups = listOf(
        SettingItemGroup(
            label = stringResource(Res.string.settings_first_group),
            textStyle = textStyle,
            childTextStyle = childTextStyle,
            items = listOf(
                SettingItem(
                    icon = if (rememberLogin.value) painterResource(Res.drawable.lock) else painterResource(Res.drawable.unlock),
                    description = "Save username and password after logging in",
                    label = "Remember Login",
                    enabled = true,
                    initialState = rememberLogin.value,
                    onClick = {
                        viewModel.toggleRememberLogin()
                        true
                    },
                    type = SettingType.SWITCH
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.home),
                    description = "Main screen tab shown on start",
                    label = "Start Tab",
                    enabled = true,
                    initialState = tabNames.getOrElse(defaultTab.value) { "" },
                    onClick = {
                        viewModel.setDefaultTab(defaultTab.value + 1)
                        true
                    },
                    type = SettingType.CLICKABLE_TEXT
                )
            )
        ),
        SettingItemGroup(
            label = stringResource(Res.string.settings_second_group),
            textStyle = textStyle,
            childTextStyle = childTextStyle,
            items = listOf(
                SettingItem(
                    icon = painterResource(Res.drawable.server),
                    description = "Url used to connect to the server",
                    label = "Server Url",
                    enabled = true,
                    initialState = serverUrl.value,
                    onClick = { url ->
                        try {
                            viewModel.setServerUrl(url as? String ?: "")
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
                    label = "Device Name",
                    enabled = true,
                    initialState = deviceName.value,
                    onClick = { name ->
                        viewModel.setDeviceName(name as? String ?: "")
                        true
                    },
                    type = SettingType.TEXT
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.list),
                    description = "Number of items loaded per page " +
                            "(${MainViewModel.PAGE_SIZE_RANGE.first}-${MainViewModel.PAGE_SIZE_RANGE.last})",
                    label = "Page Size",
                    enabled = true,
                    initialState = pageSize.value.toString(),
                    onClick = { value ->
                        (value as? String)?.trim()?.toIntOrNull()
                            ?.takeIf { it in MainViewModel.PAGE_SIZE_RANGE }
                            ?.let { viewModel.setPageSize(it); true } ?: false
                    },
                    type = SettingType.TEXT
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.search),
                    description = "Max number of search results " +
                            "(${MainViewModel.SEARCH_LIMIT_RANGE.first}-${MainViewModel.SEARCH_LIMIT_RANGE.last})",
                    label = "Search Result Limit",
                    enabled = true,
                    initialState = searchLimit.value.toString(),
                    onClick = { value ->
                        (value as? String)?.trim()?.toIntOrNull()
                            ?.takeIf { it in MainViewModel.SEARCH_LIMIT_RANGE }
                            ?.let { viewModel.setSearchLimit(it); true } ?: false
                    },
                    type = SettingType.TEXT
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.database),
                    description = "Max items kept in each cache, applies on restart " +
                            "(${MainViewModel.CACHE_SIZE_RANGE.first}-${MainViewModel.CACHE_SIZE_RANGE.last})",
                    label = "Cache Size",
                    enabled = true,
                    initialState = cacheSize.value.toString(),
                    onClick = { value ->
                        (value as? String)?.trim()?.toIntOrNull()
                            ?.takeIf { it in MainViewModel.CACHE_SIZE_RANGE }
                            ?.let { viewModel.setCacheSize(it); true } ?: false
                    },
                    type = SettingType.TEXT
                )
            )
        ),
        SettingItemGroup(
            label = "Account & Station",
            textStyle = textStyle,
            childTextStyle = childTextStyle,
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
                    label = "Counter Station",
                    enabled = true,
                    initialState = "",
                    onClick = {
                        showStation = true
                        true
                    },
                    type = SettingType.CLICKABLE_TEXT
                )
            )
        ),
        SettingItemGroup(
            label = stringResource(Res.string.settings_third_group),
            textStyle = textStyle,
            childTextStyle = childTextStyle,
            items = listOf(
                SettingItem(
                    icon = if (darkMode.value) painterResource(Res.drawable.moon) else painterResource(Res.drawable.sun),
                    description = "UI dark mode or light mode",
                    label = "Dark Mode",
                    enabled = true,
                    initialState = darkMode.value,
                    onClick = {
                        viewModel.toggleDarkMode()
                        true
                    },
                    type = SettingType.SWITCH
                )
            )
        )
    )

    Surface(
        modifier = modifier
            .padding(8.dp)
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        PlatformLazyColumn(
            modifier = Modifier.padding(16.dp),
            isVisible = true
        ) {
            items(settingsGroups) { group ->
                SettingsGroup(settingItemGroup = group)
            }
        }
    }
}