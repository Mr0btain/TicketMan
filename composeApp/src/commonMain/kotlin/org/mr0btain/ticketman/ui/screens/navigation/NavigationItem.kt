package org.mr0btain.ticketman.ui.screens.navigation

import org.jetbrains.compose.resources.DrawableResource

data class NavigationItem(
    val title: String,
    val icon: DrawableResource,
    val destination: Destination
)
