@file:Suppress("FunctionName")

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import components.MainViewModel
import components.injection.mainModule
import org.koin.compose.KoinApplication
import org.koin.compose.getKoin
import screens.navigation.DrawerNav
import screens.navigation.NavigationHost

@Composable
fun App() {
    val platform = getPlatform()

    KoinApplication(application = { modules(mainModule) }) {
        val viewModel = getKoin().get<MainViewModel>()
        val navigator = rememberNavController()

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