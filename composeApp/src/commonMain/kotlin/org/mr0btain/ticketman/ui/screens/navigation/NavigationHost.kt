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
import org.mr0btain.ticketman.ui.screens.login.LoginScreen
import org.mr0btain.ticketman.ui.screens.main.MainScreen
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.SettingsScreen
import org.mr0btain.ticketman.ui.screens.settings.components.SettingsViewModel

@Composable
fun NavigationHost(
    modifier: Modifier = Modifier,
    navHostController: NavHostController,
    startDestination: String = Screen.Main.route,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    mainViewModel: MainViewModel,
    snackBarState: SnackbarHostState
) {
    val darkMode = mainViewModel.darkMode.collectAsState()
    val scrollBarsOnRight = mainViewModel.scrollBarsOnRight.collectAsState()
    val scrollBarsOnBottom = mainViewModel.scrollBarsOnBottom.collectAsState()

    NavHost(
        modifier = modifier,
        navController = navHostController,
        startDestination = startDestination
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                viewModel = mainViewModel,
                coroutineScope = coroutineScope,
                isDarkMode = darkMode.value,
                snackBarState = snackBarState,
                scrollBarsOnRight = scrollBarsOnRight.value,
                scrollBarsOnBottom = scrollBarsOnBottom.value
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = mainViewModel)
        }

        composable(Screen.Login.route) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = modifier.fillMaxSize()
            ) {
                LoginScreen(
                    onConfirm = { user, pass ->
                        mainViewModel.updateUsername(user)
                        mainViewModel.updatePassword(pass)

                        true
                    },
                    onDismiss = {},
                    isDarkMode = darkMode.value,
                    snackBarState = snackBarState,
                    viewModel = mainViewModel
                )
            }
        }

        composable(Screen.Admin.route) {
            AdminScreen(
                viewModel = mainViewModel,
                isDarkMode = darkMode.value,
                snackBarState = snackBarState
            )
        }
    }
}