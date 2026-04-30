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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformLazyColumn
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItem
import org.mr0btain.ticketman.ui.screens.settings.components.SettingItemGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SettingType
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SwitchSetting
import org.mr0btain.ticketman.ui.screens.settings.components.TextSetting
import ticketman.composeapp.generated.resources.*

// TODO: Add option for smooth scroll to item on selection or immediate
// TODO: Readd scroll bar size options now that we've hashed out the scroll bars

// For now, I think well be fine with using this on every screen size/ platform as it should fit just fine
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    scope: CoroutineScope
) {
    val TAG = "SettingsScreen"
    val CHECK_URL_TAG = "$TAG:CheckUrl"

    val autoSync = viewModel.autoSyncDatabase.collectAsState()
    val darkMode = viewModel.darkMode.collectAsState()

    // Server Url Stuff
    val serverUrl = viewModel.serverAddress.collectAsState()
    var showUrlEditDialog by remember { mutableStateOf(false) }

    val textStyle = MaterialTheme.typography.bodyMedium
    val childTextStyle = MaterialTheme.typography.bodySmall

    val settingsGroups = listOf(
        SettingItemGroup(
            label = stringResource(Res.string.settings_first_group),
            textStyle = textStyle,
            childTextStyle = childTextStyle,
            items = listOf()
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
                    initialState = viewModel.serverAddress.value,
                    onClick = { url ->
                        try {
                            viewModel.updateServerUrl(url as? String ?: "")

                            true
                            /*
                            if ( viewModel.dataHandler.checkNewServerConnection(url)) {
                                viewModel.updateServerUrl(url)
                                isUrlError = false
                                showUrlEditDialog = false
                                AppLogger.m(CHECK_URL_TAG, SimpleError.SUCCESS)
                            } else isUrlError = true
                             */
                        } catch (ex: Exception) {
                            AppLogger.e(CHECK_URL_TAG, SimpleResult.FAIL, ex)
                            false
                        }
                    },
                    type = SettingType.TEXT
                ),
                SettingItem(
                    icon = painterResource(Res.drawable.download_cloud),
                    description = "Auto sync with server",
                    label = "Server Auto Sync",
                    enabled = true,
                    initialState = viewModel.autoSyncDatabase.value,
                    onClick = {
                        viewModel.toggleAutoSync()
                        true
                              },
                    type = SettingType.SWITCH
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