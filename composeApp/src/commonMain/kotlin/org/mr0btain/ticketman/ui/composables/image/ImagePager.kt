package org.mr0btain.ticketman.ui.composables.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.utils.decodeBase64ToImageBitmap
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.arrow_left_circle
import ticketman.composeapp.generated.resources.arrow_right_circle
import ticketman.composeapp.generated.resources.chevron_left
import ticketman.composeapp.generated.resources.chevron_right
import kotlin.math.absoluteValue

@Composable
fun ImagePager(
    modifier: Modifier = Modifier,
    imageData: List<String>,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    imageMaxWidth: Dp,
    imageMaxHeight: Dp
) {
    val pagerState = rememberPagerState(pageCount = { imageData.size })
    val images = remember(imageData.size) { imageData.map { decodeBase64ToImageBitmap(it) } }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Previous image button
        IconButton(
            onClick = {
                coroutineScope.launch {
                    if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            }
        ) {
            Icon(
                painter = painterResource(Res.drawable.chevron_left),
                contentDescription = "Last Photo"
            )
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
            Card(
                Modifier
                    .graphicsLayer {
                        // Calculate the absolute offset for the current page from the
                        // scroll position. We use the absolute value which allows us to mirror
                        // any effects for both directions
                        val pageOffset = (
                                (pagerState.currentPage - page) + pagerState
                                    .currentPageOffsetFraction
                                ).absoluteValue

                        // We animate the alpha, between 50% and 100%
                        alpha = lerp(
                            start = 0.5f,
                            stop = 1f,
                            fraction = 1f - pageOffset.coerceIn(0f, 1f)
                        )
                    }
            ) {
                // Actual image
                if (images.size >= page) {
                    images[page]?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "",
                        )
                    }
                }
            }
        }

        // Next image button
        IconButton(
            onClick = {
                coroutineScope.launch {
                    if (pagerState.currentPage + 1 <= images.size) pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        ) {
            Icon(
                painter = painterResource(Res.drawable.chevron_right),
                contentDescription = "Last Photo"
            )
        }
    }
}