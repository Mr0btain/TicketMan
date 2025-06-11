package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.ui.screens.admin.AdminScreen
import org.mr0btain.ticketman.ui.screens.inventory.InventoryScreen
import org.mr0btain.ticketman.ui.screens.login.LoginScreen
import org.mr0btain.ticketman.ui.screens.main.MainScreen
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.SettingsScreen
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel

@Composable
fun NavigationHost(
    modifier: Modifier = Modifier,
    navHostController: NavHostController,
    startDestination: String = NavigationItem.Main.route,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    snackBarState: SnackbarHostState
) {
    val darkMode = settingsViewModel.darkMode.collectAsState()
    val scrollBarsOnRight = settingsViewModel.scrollBarsOnRight.collectAsState()
    val scrollBarsOnBottom = settingsViewModel.scrollBarsOnBottom.collectAsState()

    NavHost(
        modifier = modifier,
        navController = navHostController,
        startDestination = startDestination
    ) {
        composable(NavigationItem.Main.route) {
            MainScreen(
                viewModel = mainViewModel,
                coroutineScope = coroutineScope,
                isDarkMode = darkMode.value,
                snackBarState = snackBarState,
                scrollBarsOnRight = scrollBarsOnRight.value,
                scrollBarsOnBottom = scrollBarsOnBottom.value
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
                    isDarkMode = darkMode.value,
                    snackBarState = snackBarState
                )
            }
        }

        composable(NavigationItem.Admin.route) {
            AdminScreen(
                viewModel = mainViewModel,
                isDarkMode = darkMode.value,
                snackBarState = snackBarState
            )
        }
    }
}