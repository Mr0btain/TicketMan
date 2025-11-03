package org.mr0btain.ticketman.ui.composables.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.image.ImageData
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_left
import ticketman.composeapp.generated.resources.chevron_right

@Composable
fun ImagePager(
    modifier: Modifier = Modifier,
    imageData: List<ImageData>,
    onImageClick: () -> Unit = {},
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    clickEnabled: Boolean = true
) {
    val pagerState = rememberPagerState(pageCount = { imageData.size })
    // Used to determine whether a page has been added or not. If it has, then we scroll to the most recent or last page
    var pageCount by remember { mutableIntStateOf(imageData.size) }

    Box {
        HorizontalPager(
            modifier = modifier
                .background(Color.Transparent)
                .padding(8.dp),
            state = pagerState,
            snapPosition = SnapPosition.Center
        ) { page ->
            // Actual image
            if (imageData.size >= page) {
                Box(
                    modifier = Modifier
                        .padding(start = 40.dp, end = 40.dp)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(enabled = clickEnabled) { onImageClick() },
                        bitmap = imageData[page].image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit
                    )
                }

            }
        }

        IconButton(
            modifier = Modifier.align(Alignment.CenterStart),
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

        // Next image button
        IconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
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