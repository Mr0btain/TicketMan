@file:Suppress("FunctionName")

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import components.MainViewModel
import components.injection.viewModelModule
import org.koin.compose.KoinApplication
import org.koin.compose.KoinContext
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import screens.navigation.DrawerNav
import screens.navigation.NavigationHost
import ui.theme.AppTheme

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    KoinContext {
        val viewModel = koinViewModel<MainViewModel>()
        val navigator = rememberNavController()
        val windowSizeClass = calculateWindowSizeClass()

        AppTheme(
            darkTheme = viewModel.darkMode.value
        ) {
            if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded) {
                DrawerNav(
                    navHostController = navigator,
                    viewModel = viewModel,
                    content = { paddingValues ->
                        NavigationHost(
                            modifier = Modifier.padding(paddingValues),
                            navHostController = navigator,
                            viewModel = viewModel
                        )
                    },
                )
            } else {
                DrawerNav(
                    navHostController = navigator,
                    viewModel = viewModel,
                    content = { paddingValues ->
                        NavigationHost(
                            modifier = Modifier.padding(paddingValues),
                            navHostController = navigator,
                            viewModel = viewModel
                        )
                    },
                )
            }
        }
    }
}