package org.mr0btain.ticketman.ui.composables.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.image.ImageData
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_left
import ticketman.composeapp.generated.resources.chevron_right
import kotlin.math.absoluteValue

@Composable
fun ImagePager(
    modifier: Modifier = Modifier,
    imageData: List<ImageData>,
    onImageClick: () -> Unit,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    imageMaxWidth: Dp,
    imageMaxHeight: Dp,
    clickEnabled: Boolean = true

) {
    val pagerState = rememberPagerState(pageCount = { imageData.size })
    // If there's only one image there's no reason to show buttons
    val shouldShowButtons = imageData.size > 1
    // Used to determine whether a page has been added or not. If it has, then we scroll to the most recent or last page
    var pageCount by remember { mutableIntStateOf(imageData.size) }

    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Previous image button
        if (shouldShowButtons) {
            IconButton(
                enabled = pagerState.currentPage > 0,
                onClick = {
                    coroutineScope.launch {
                        if (pagerState.currentPage > 0)
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chevron_left),
                    contentDescription = "Last Photo"
                )
            }
        }

        HorizontalPager(
            modifier = Modifier
                .sizeIn(
                    maxWidth = imageMaxWidth,
                    maxHeight = imageMaxHeight
                ),
            state = pagerState,
            pageSpacing = 2.dp
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Actual image
                if (imageData.size >= page) {
                    Image(
                        modifier = if (clickEnabled)
                            Modifier.clickable(onClick = { onImageClick() })
                        else Modifier,
                        bitmap = imageData[page].image,
                        contentDescription = ""
                    )
                }
            }
        }

        if (shouldShowButtons) {
            // Next image button
            IconButton(
                enabled = pagerState.currentPage != imageData.size - 1,
                onClick = {
                    coroutineScope.launch {
                        if (pagerState.currentPage + 1 <= imageData.size)
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.chevron_right),
                    contentDescription = "Next Photo"
                )
            }
        }
    }

    // If our list has changed, then we should change our scroll to either the last or the first depending on if it
    // is an addition or removal
    LaunchedEffect(imageData.size) {
        if (imageData.size > pageCount) {
            pagerState.scrollToPage(imageData.size - 1)
        } else if (imageData.size < pageCount) {
             pagerState.scrollToPage(0)
        }
    }
}