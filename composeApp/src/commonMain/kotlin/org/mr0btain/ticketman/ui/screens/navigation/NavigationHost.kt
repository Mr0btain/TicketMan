package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.navigation.compose.currentBackStackEntryAsState
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.arrow_left
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.mr0btain.ticketman.ui.kit.gallery.KitGallery
import org.mr0btain.ticketman.ui.screens.admin.AdminScreen
import org.mr0btain.ticketman.ui.screens.counter.CounterScreen
import org.mr0btain.ticketman.ui.screens.records.RecordsScreen
import org.mr0btain.ticketman.ui.MainViewModel
import org.mr0btain.ticketman.ui.screens.purchasing.PurchasingScreen
import org.mr0btain.ticketman.ui.screens.appointments.AppointmentsScreen
import org.mr0btain.ticketman.ui.screens.settings.SettingsScreen

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NavigationHost(
    modifier: Modifier = Modifier,
    navHostController: NavHostController,
    initialDestination: Destination = Destination.RECORDS,
    mainViewModel: MainViewModel
) {
    // The screen an admin was sent to from the admin screen, which then offers a way back until they go elsewhere
    var visitFromAdmin by rememberSaveable { mutableStateOf<String?>(null) }
    val currentRoute = navHostController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute != visitFromAdmin) visitFromAdmin = null
    }

    fun visitFromAdmin(destination: Destination) {
        visitFromAdmin = destination.route
        navHostController.navigateTo(destination.route)
    }

    @Composable
    fun BackToAdmin(content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xs)) {
            if (visitFromAdmin != null && visitFromAdmin == currentRoute) {
                val back = { navHostController.navigateTo(Destination.ADMIN.route) }
                BackHandler(onBack = back)
                TmButton(text = "Back to Admin", onClick = back, variant = TmButtonVariant.GHOST, leadingIcon = Res.drawable.arrow_left)
            }
            content()
        }
    }

    NavHost(
        modifier = modifier,
        navController = navHostController,
        startDestination = initialDestination.route
    ) {
        composable(Destination.ADMIN.route) {
            AdminScreen(
                viewModel = mainViewModel,
                onNavigateToMain = { visitFromAdmin(Destination.RECORDS) },
                onOpenTicketAtCounter = { identifier ->
                    mainViewModel.counter.openTicket(identifier)
                    visitFromAdmin(Destination.COUNTER)
                }
            )
        }

        composable(Destination.COUNTER.route) {
            BackToAdmin {
                CounterScreen(
                    viewModel = mainViewModel
                )
            }
        }

        composable(Destination.PURCHASING.route) {
            PurchasingScreen(viewModel = mainViewModel)
        }

        composable(Destination.APPOINTMENTS.route) {
            AppointmentsScreen(
                viewModel = mainViewModel,
                onCheckInStarted = { navHostController.navigateTo(Destination.COUNTER.route) }
            )
        }

        composable(Destination.RECORDS.route) {
            BackToAdmin { RecordsScreen(viewModel = mainViewModel) }
        }

        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                viewModel = mainViewModel,
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