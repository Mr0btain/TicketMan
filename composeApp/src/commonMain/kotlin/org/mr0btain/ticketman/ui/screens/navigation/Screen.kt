package org.mr0btain.ticketman.ui.screens.navigation

sealed class Screen(val route: String) {
    data object Main: Screen(Destinations.MAIN.name)
    data object Settings: Screen(Destinations.SETTINGS.name)
    data object Login: Screen(Destinations.LOGIN.name)
    data object Admin: Screen(Destinations.ADMIN.name)
    data object Dashboard: Screen(Destinations.DASHBOARD.name)
}