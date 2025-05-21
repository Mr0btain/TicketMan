package screens.navigation

import components.MainViewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import screens.login.LoginScreen
import screens.settings.SettingsScreen
import screens.settings.components.SettingsViewModel

@Composable
fun NavigationHost(
    modifier: Modifier = Modifier,
    navHostController: NavHostController,
    startDestination: String = NavigationItem.Home.route,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        modifier = modifier,
        navController = navHostController,
        startDestination = startDestination
    ) {
        composable(NavigationItem.Home.route) {
            //HomeScreen(viewModel = viewModel)
        }

        composable(NavigationItem.Settings.route) {
            SettingsScreen(viewModel = settingsViewModel)
        }

        composable(NavigationItem.Login.route) {
            LoginScreen(viewModel = mainViewModel)
        }
    }
}