package org.mr0btain.ticketman.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.composables.scrollbar.PlatformColumnScrollable
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsSwitchItem
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsTextItem
import ticketman.composeapp.generated.resources.*

// TODO: Add option for smooth scroll to item on selection or immediate
// TODO: Readd scroll bar size options now that we've hashed out the scroll bars
// TODO: Add option for scroll bar size enum

// For now, I think well be fine with using this on every screen size/ platform as it should fit just fine
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    scope: CoroutineScope
) {
    val TAG = "SettingsScreen"
    val CHECK_URL_TAG = "$TAG:CheckUrl"


    val autoSync = viewModel.autoSyncDatabase.collectAsState(false)
    val darkMode = viewModel.darkMode.collectAsState(false)
    val scrollBarsOnBottom = viewModel.scrollBarsOnBottom.collectAsState()
    val scrollBarsOnRight = viewModel.scrollBarsOnRight.collectAsState()

    // Server Url Stuff
    val serverUrl = viewModel.serverAddress.collectAsState()
    var isUrlError by remember { mutableStateOf(false) }
    var showUrlEditDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
        color = if (darkMode.value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background
    ) {
        PlatformColumnScrollable(
            modifier = Modifier.padding(16.dp),
            backgroundColor = MaterialTheme.colorScheme.background,
            isVisible = true
        ) {
            SettingsGroup(stringResource(Res.string.settings_first_group)) {

            }

            SettingsGroup(name = stringResource(Res.string.settings_second_group)) {
                // Server address item
                SettingsTextItem(
                    name = "Server Url:",
                    icon = painterResource(Res.drawable.server),
                    iconDesc = "Description",
                    value = serverUrl.value,
                    onConfirmChange = { url ->
                        scope.launch {
                            try {
                                if ( viewModel.dataHandler.checkNewServerConnection(url)) {
                                    viewModel.updateServerUrl(url)
                                    isUrlError = false
                                    showUrlEditDialog = false
                                    AppLogger.m(CHECK_URL_TAG, SimpleError.SUCCESS)
                                } else isUrlError = true
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                                AppLogger.i(CHECK_URL_TAG, SimpleError.FAIL)
                                isUrlError = true
                            }
                        }
                    },
                    inputFilter = { text -> text },
                    keyboardType = KeyboardType.Text,
                    isError = isUrlError,
                    showDialog = showUrlEditDialog,
                    surfaceOnClick = { showUrlEditDialog = true},
                    dialogDismiss = { showUrlEditDialog = false }
                )

                // Dark mode switch
                SettingsSwitchItem(
                    name = "Auto Sync With Server",
                    icon = painterResource(Res.drawable.download_cloud),
                    iconDesc = "Auto sync data with server",
                    state = autoSync.value
                ) {
                    viewModel.toggleAutoSync()
                }
            }

            SettingsGroup(stringResource(Res.string.settings_third_group)) {
                // Dark mode switch
                SettingsSwitchItem(
                    name = "Dark Mode",
                    icon = if (darkMode.value) painterResource(Res.drawable.moon) else painterResource(Res.drawable.sun),
                    iconDesc = "Should the ui stay in dark mode",
                    state = darkMode.value
                ) {
                    viewModel.toggleDarkMode()
                }

                // Scroll bars on bottom
                SettingsSwitchItem(
                    name = "Horizontal ScrollBars on the Bottom",
                    icon = if (scrollBarsOnBottom.value) painterResource(Res.drawable.arrow_down_circle) else painterResource(Res.drawable.arrow_up_circle),
                    iconDesc = "Position of horizontal scroll bars",
                    state = scrollBarsOnBottom.value
                ) {
                    viewModel.toggleScrollBarsOnBottom()
                }

                // Scroll bars on right
                SettingsSwitchItem(
                    name = "Vertical ScrollBars on the Right",
                    icon = if (scrollBarsOnRight.value) painterResource(Res.drawable.arrow_right_circle) else painterResource(Res.drawable.arrow_left_circle),
                    iconDesc = "Position of vertical scroll bars",
                    state = scrollBarsOnRight.value
                ) {
                    viewModel.toggleScrollBarsOnRight()
                }
            }
        }
    }
}