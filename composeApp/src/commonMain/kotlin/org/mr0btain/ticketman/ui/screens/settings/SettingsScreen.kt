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

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel
) {
    val darkMode = viewModel.darkMode.collectAsState(false)
    val scrollBarOnRight = viewModel.scrollBarOnRight.collectAsState(true)
    val scrollBarOnTop = viewModel.scrollBarOnTop.collectAsState(false)

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
                        state = darkMode
                    ) {
                        viewModel.toggleDarkMode()
                    }

                    // Vertical Scroll Bar position switch
                    SettingsSwitchItem(
                        name = "Vertical ScrollBars on the right",
                        icon = if (scrollBarOnRight.value) painterResource(Res.drawable.arrow_right) else painterResource(Res.drawable.arrow_left),
                        iconDesc = "Should scroll bars be on the right? if not they will be on the left",
                        state = scrollBarOnRight
                    ) {
                        viewModel.toggleScrollBarOnRight()
                    }

                    // Horizontal Scroll Bar position switch
                    SettingsSwitchItem(
                        name = "Horizontal ScrollBars on top",
                        icon = if (scrollBarOnTop.value) painterResource(Res.drawable.arrow_up) else painterResource(Res.drawable.arrow_down),
                        iconDesc = "Should scroll bars be on the top? if not they will be on the bottom",
                        state = scrollBarOnTop
                    ) {
                        viewModel.toggleScrollBarOnTop()
                    }
                }
            }
        }
    }
}