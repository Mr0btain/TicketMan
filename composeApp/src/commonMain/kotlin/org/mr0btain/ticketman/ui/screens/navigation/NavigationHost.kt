package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.mr0btain.ticketman.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.login.LoginScreen
import org.mr0btain.ticketman.ui.screens.main.MainScreen
import org.mr0btain.ticketman.ui.screens.settings.SettingsScreen
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel

@Composable
fun NavigationHost(
    modifier: Modifier = Modifier,
    navHostController: NavHostController,
    startDestination: String = NavigationItem.Main.route,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        modifier = modifier,
        navController = navHostController,
        startDestination = startDestination
    ) {
        composable(NavigationItem.Main.route) {
            MainScreen(
                viewModel = mainViewModel,
                isDarkMode = settingsViewModel.isDarkMode.collectAsState()
            )
        }

        composable(NavigationItem.Settings.route) {
            SettingsScreen(viewModel = settingsViewModel)
        }

        composable(NavigationItem.Login.route) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = modifier.fillMaxSize()
            ) {
                LoginScreen(
                    onConfirm = { user, pass -> true },
                    onDismiss = {},
                    isDarkMode = settingsViewModel.isDarkMode.collectAsState()
                )
            }
        }
    }
}