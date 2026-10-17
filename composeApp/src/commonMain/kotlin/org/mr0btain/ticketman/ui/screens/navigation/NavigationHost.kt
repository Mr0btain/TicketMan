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
import org.mr0btain.ticketman.ui.screens.counter.CounterScreen
import org.mr0btain.ticketman.ui.screens.invoice.InvoiceScreen
import org.mr0btain.ticketman.ui.screens.login.LoginScreen
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
    val scrollBarsOnRight = mainViewModel.scrollBarsOnRight.collectAsState()
    val scrollBarsOnBottom = mainViewModel.scrollBarsOnBottom.collectAsState()

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

        // TEMPORARY
        composable(Destination.LOGIN.route) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = modifier.fillMaxSize()
            ) {
                LoginScreen(
                    onConfirm = { user, pass -> mainViewModel.login(user, pass) },
                    snackBarState = snackBarState,
                    viewModel = mainViewModel
                )
            }
        }

        composable(Destination.MAIN.route) {
            MainScreen(
                viewModel = mainViewModel,
                scope = coroutineScope,
                snackBarState = snackBarState,
                scrollBarsOnRight = scrollBarsOnRight.value,
                scrollBarsOnBottom = scrollBarsOnBottom.value
            )
        }

        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                viewModel = mainViewModel,
                scope = coroutineScope
            )
        }
    }
}