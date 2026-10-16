package org.mr0btain.ticketman.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
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
    val serverUrl = viewModel.serverAddress.collectAsState()

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
                    initialState = serverUrl,
                    onClick = { url ->
                        try {
                            viewModel.updateServerUrl(url as? String ?: "")
                            true
                        } catch (ex: Exception) {
                            AppLogger.e(CHECK_URL_TAG, SimpleResult.FAIL, ex)
                            false
                        }
                    },
                    type = SettingType.TEXT
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