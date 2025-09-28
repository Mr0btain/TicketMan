package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.ui.graphics.painter.Painter

data class NavigationItem(
    val title: String,
    val icon: Painter,
    val destination: Destination
)