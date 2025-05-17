package screens.navigation

sealed class NavigationItem(val route: String) {
    data object Home : NavigationItem(Destinations.HOME.name)
    data object Settings : NavigationItem(Destinations.SETTINGS.name)
    data object Login : NavigationItem(Destinations.LOGIN.name)
}