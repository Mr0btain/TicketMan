package screens.settings

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
import screens.settings.components.SettingsGroup
import screens.settings.components.SettingsSwitchItem
import screens.settings.components.SettingsTextItem
import screens.settings.components.SettingsViewModel
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.book
import ticketman.composeapp.generated.resources.hash
import ticketman.composeapp.generated.resources.moon
import ticketman.composeapp.generated.resources.settings_first_group
import ticketman.composeapp.generated.resources.settings_second_group
import ticketman.composeapp.generated.resources.sun

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel
) {
    val darkMode = viewModel.isDarkMode.collectAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
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
                    SettingsSwitchItem(
                        name = "Dark Mode",
                        icon = if (darkMode.value) painterResource(Res.drawable.moon) else painterResource(Res.drawable.sun),
                        iconDesc = "Example Description",
                        state = darkMode
                    ) {
                        viewModel.toggleDarkMode()
                    }

                    SettingsSwitchItem(
                        name = "Switch Item",
                        icon = painterResource(Res.drawable.sun),
                        iconDesc = "Example Description",
                        state = viewModel.isSwitchOn.collectAsState()
                    ) {
                        viewModel.toggleSwitch()
                    }

                    SettingsTextItem(
                        name = "Text Item",
                        icon = painterResource(Res.drawable.book),
                        iconDesc = "Description",
                        state = viewModel.textPreference.collectAsState(),
                        onSave = { finalText -> viewModel.saveText(finalText) },
                        inputFilter = { text -> text },
                        onCheck = { text -> viewModel.checkTextInput(text) },
                        keyboardType = KeyboardType.Text
                    )
                }

                SettingsGroup(name = stringResource(Res.string.settings_second_group)) {
                    SettingsTextItem(
                        name = "Number Item",
                        icon = painterResource(Res.drawable.hash),
                        iconDesc = "Description",
                        state = viewModel.numberPreference.collectAsState(),
                        onSave = { finalText -> viewModel.saveNumber(finalText) },
                        inputFilter = { text -> viewModel.filterNumbers(text) },
                        onCheck = { text -> viewModel.checkNumber(text) },
                        keyboardType = KeyboardType.Number
                    )
                }
            }
        }
    }
}


/*
Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.settings_text),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.padding(12.dp))

                // Use dark mode option
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        modifier = Modifier.semantics { contentDescription = "If enabled will use dark mode all the time" },
                        text = "Dark Mode",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.padding(16.dp))

                    Switch(
                        checked = ,
                        onCheckedChange = {
                            viewModel.darkMode.value = !viewModel.darkMode.value
                        }
                    )
                }
 */