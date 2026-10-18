package org.mr0btain.ticketman.ui.screens.navigation

/**
 * Enum for possible destinations for navigation, They will be ordered by their index value.
 * @param route The route for navigating to this destination
 * @param index The order by which this will be listed in the navigation UI
 */

enum class Destination(val route: String, val index: Int) {
    ADMIN("Admin", 0),
    COUNTER("Counter", 1),
    INVOICE("Invoice", 2),
    RECORDS("Records", 4),
    SETTINGS("Settings", 5),

    /** UI kit component gallery, listed in debug builds and opened from Settings */
    GALLERY("Gallery", 6);
}