package org.mr0btain.ticketman.ui.screens.navigation

/**
 * Enum for possible destinations for navigation, They will be ordered by their index value.
 * @param route The route for navigating to this destination
 * @param index The order by which this will be listed in the navigation UI
 */

enum class Destination(val route: String, val index: Int) {
    DASHBOARD("Dash", 0),
    MAIN("Main", 1),
    LOGIN("Login", 2),
    SETTINGS("Settings", 3),
    ADMIN("Admin", 4);
}