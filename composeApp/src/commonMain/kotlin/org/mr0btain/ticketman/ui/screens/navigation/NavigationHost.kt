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
import org.mr0btain.ticketman.ui.screens.records.RecordsScreen
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.screens.settings.SettingsScreen

@Composable
fun NavigationHost(
    modifier: Modifier = Modifier,
    navHostController: NavHostController,
    initialDestination: Destination = Destination.RECORDS,
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
                onNavigateToMain = { navHostController.navigateTo(Destination.RECORDS.route) },
                onOpenTicketAtCounter = { identifier ->
                    mainViewModel.counter.openTicket(identifier)
                    navHostController.navigateTo(Destination.COUNTER.route)
                }
            )
        }

        composable(Destination.COUNTER.route) {
            CounterScreen(
                viewModel = mainViewModel
            )
        }

        composable(Destination.RECORDS.route) {
            RecordsScreen(viewModel = mainViewModel)
        }

        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                viewModel = mainViewModel,
                scope = coroutineScope,
                onOpenGallery = { navHostController.navigate(Destination.GALLERY.route) }
            )
        }

        composable(Destination.GALLERY.route) {
            val darkMode = mainViewModel.settings.darkMode.collectAsState()

            KitGallery(
                palette = mainViewModel.settings.palette.collectAsState().value,
                onPaletteChange = mainViewModel.settings::setPalette,
                font = mainViewModel.settings.font.collectAsState().value,
                onFontChange = mainViewModel.settings::setFont,
                darkTheme = darkMode.value,
                onDarkThemeChange = { dark -> if (dark != darkMode.value) mainViewModel.settings.toggleDarkMode() },
                reduceTransparency = mainViewModel.settings.reduceTransparency.collectAsState().value,
                onReduceTransparencyChange = { mainViewModel.settings.toggleReduceTransparency() }
            )
        }
    }
}