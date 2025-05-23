package org.mr0btain.ticketman.ui.screens.navigation

sealed class NavigationItem(val route: String) {
    data object Main : NavigationItem(Destinations.MAIN.name)
    data object Settings : NavigationItem(Destinations.SETTINGS.name)
    data object Login : NavigationItem(Destinations.LOGIN.name)
}