package org.mr0btain.ticketman.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsGroup
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsSwitchItem
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsTextItem
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel
import ticketman.composeapp.generated.resources.*

// TODO: Add option for smooth scroll to item on selection or immediate
// TODO: Readd scroll bar size options now that we've hashed out the scroll bars
// TODO: Add option for scroll bar size enum
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel
) {
    val darkMode = viewModel.darkMode.collectAsState(false)
    val scrollBarsOnBottom = viewModel.scrollBarsOnBottom.collectAsState()
    val scrollBarsOnRight = viewModel.scrollBarsOnRight.collectAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize(),
            color = if (darkMode.value) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SettingsGroup(stringResource(Res.string.settings_first_group)) {

                }

                SettingsGroup(name = stringResource(Res.string.settings_second_group)) {
                    // Server address item
                    SettingsTextItem(
                        name = "Server Address:",
                        icon = painterResource(Res.drawable.server),
                        iconDesc = "Description",
                        state = viewModel.serverAddress.collectAsState(""),
                        onSave = { address -> viewModel.saveServerAddress(address) },
                        inputFilter = { text -> text },
                        onCheck = { text -> viewModel.checkWebAddress(text) },
                        keyboardType = KeyboardType.Text
                    )
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
}