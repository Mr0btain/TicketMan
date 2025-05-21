@file:Suppress("FunctionName")

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import components.MainViewModel
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import screens.navigation.DrawerNav
import screens.navigation.NavigationHost
import screens.settings.components.SettingsViewModel
import ui.theme.AppTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    KoinContext {
        // View Models
        val mainViewModel = koinViewModel<MainViewModel>()
        val settingsViewModel = koinViewModel<SettingsViewModel>()

        // Navigation
        val navigator = rememberNavController()

        // Calculating screen size stuff
        val windowSizeClass = calculateWindowSizeClass()

        // Main scope to use with coroutines
        val mainScope = rememberCoroutineScope()

        //

        AppTheme(
            darkTheme = settingsViewModel.isDarkMode.collectAsState().value
        ) {
            if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                DrawerNav(
                    navHostController = navigator,
                    content = { paddingValues ->
                        NavigationHost(
                            modifier = Modifier.padding(paddingValues),
                            navHostController = navigator,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    },
                    scope = mainScope,
                    topAppTextState = mainViewModel.topAppBarText
                )
            } else {
                DrawerNav(
                    navHostController = navigator,
                    content = { paddingValues ->
                        NavigationHost(
                            modifier = Modifier.padding(paddingValues),
                            navHostController = navigator,
                            mainViewModel = mainViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    },
                    scope = mainScope,
                    topAppTextState = mainViewModel.topAppBarText
                )
            }
        }
    }
}