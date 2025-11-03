package org.mr0btain.ticketman.ui.screens.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.composables.TabColumn
import ticketman.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun SideAppNav(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
    coroutineScope: CoroutineScope,
    navHostController: NavHostController,
    topAppText: String,
    destinations: List<NavigationItem>,
    initialDestination: Destination
) {
    val iconSize = 48.dp
    val iconInnerPadding = 8.dp
    val iconOuterPadding = 4.dp

    // Selected tab/ screen
    var selectedTab by remember { mutableIntStateOf(initialDestination.index) }

    // Sidebar visibility
    var isSideBarVisible by remember { mutableStateOf(true) }

    // Duration for animations
    val durationMillis = 400

    // Animated value for padding
    val sidePadding = animateIntAsState(
        targetValue = if (isSideBarVisible) 58 else 0,
        animationSpec = tween(durationMillis + 100)
        )

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    modifier = Modifier.padding(bottom = 4.dp),
                    title = { Text(topAppText) },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                .padding(iconOuterPadding)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    onClick = {
                                        isSideBarVisible = !isSideBarVisible
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(iconSize)
                                    .padding(iconInnerPadding),
                                painter = painterResource(Res.drawable.sidebar),
                                contentDescription = "Show/Hide Sidebar"
                            )
                        }
                    }
                )

                // Space between top menu icon and the nav icons
                Spacer(Modifier.padding(top = 16.dp))

                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(animationSpec = tween(durationMillis)),
                    exit = slideOutHorizontally(animationSpec = tween(durationMillis)),
                    content = {
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(sidePadding.value.dp)
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            Column(
                                modifier = Modifier,
                                verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.height(iconSize * (destinations.size + 1))
                                ) {
                                    TabColumn(
                                        modifier = Modifier.width(iconSize + 4.dp),
                                        selectedTabIndex = selectedTab
                                    ) {
                                        destinations.sortedBy { it.destination.index }.forEach { navigationItem ->
                                            Tab(
                                                modifier = Modifier.wrapContentSize(),
                                                selected = selectedTab == navigationItem.destination.index,
                                                onClick = {
                                                    coroutineScope.launch {
                                                        navHostController.navigate(navigationItem.destination.route)
                                                        selectedTab = navigationItem.destination.index
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        modifier = Modifier
                                                            .size(iconSize)
                                                            .padding(iconInnerPadding),
                                                        painter = navigationItem.icon,
                                                        contentDescription = navigationItem.title
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        content(PaddingValues(start = sidePadding.value.dp, top = 58.dp))
    }
}