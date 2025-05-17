package screens.navigation

import components.MainViewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NavigationHost(
    modifier: Modifier = Modifier,
    navHostController: NavHostController,
    startDestination: String = NavigationItem.Home.route,
    viewModel: MainViewModel
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
            //SettingsScreen(viewModel = viewModel)
        }

        composable(NavigationItem.Login.route) {
            //Login(viewModel = viewModel)
        }
    }
}