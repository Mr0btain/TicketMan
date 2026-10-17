package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.CoroutineScope
import org.mr0btain.ticketman.ui.kit.gallery.KitGallery
import org.mr0btain.ticketman.ui.screens.admin.AdminScreen
import org.mr0btain.ticketman.ui.screens.counter.CounterScreen
import org.mr0btain.ticketman.ui.screens.invoice.InvoiceScreen
import org.mr0btain.ticketman.ui.screens.main.MainScreen
import org.mr0btain.ticketman.ui.screens.main.components.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.SettingsScreen

@Composable
fun NavigationHost(
    modifier: Modifier = Modifier,
    navHostController: NavHostController,
    initialDestination: Destination = Destination.MAIN,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    mainViewModel: MainViewModel,
    snackBarState: SnackbarHostState
) {
    NavHost(
        modifier = modifier,
        navController = navHostController,
        startDestination = initialDestination.route
    ) {
        composable(Destination.ADMIN.route) {
            AdminScreen(
                viewModel = mainViewModel,
                snackBarState = snackBarState,
                coroutineScope = coroutineScope,
                onNavigateToMain = { navHostController.navigate(Destination.MAIN.route) }
            )
        }

        composable(Destination.DASHBOARD.route) {
            CounterScreen(
                viewModel = mainViewModel
            )
        }

        composable(Destination.INVOICE.route) {
            InvoiceScreen(
                viewModel = mainViewModel,
                onOpenTicket = { identifier ->
                    mainViewModel.counter.openTicket(identifier)
                    navHostController.navigate(Destination.DASHBOARD.route)
                }
            )
        }

        composable(Destination.MAIN.route) {
            MainScreen(viewModel = mainViewModel)
        }

        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                viewModel = mainViewModel,
                scope = coroutineScope,
                onOpenGallery = { navHostController.navigate(Destination.GALLERY.route) }
            )
        }

        composable(Destination.GALLERY.route) {
            val darkMode = mainViewModel.darkMode.collectAsState()

            KitGallery(
                palette = mainViewModel.palette.collectAsState().value,
                onPaletteChange = mainViewModel::setPalette,
                font = mainViewModel.font.collectAsState().value,
                onFontChange = mainViewModel::setFont,
                darkTheme = darkMode.value,
                onDarkThemeChange = { dark -> if (dark != darkMode.value) mainViewModel.toggleDarkMode() },
                reduceTransparency = mainViewModel.reduceTransparency.collectAsState().value,
                onReduceTransparencyChange = { mainViewModel.toggleReduceTransparency() }
            )
        }
    }
}