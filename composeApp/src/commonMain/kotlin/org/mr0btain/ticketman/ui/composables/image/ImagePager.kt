package org.mr0btain.ticketman.ui.composables.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.data.image.ImageData
import org.mr0btain.ticketman.components.utils.getImages
import org.mr0btain.ticketman.components.utils.toImageBitmap
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.error.SimpleError
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.ui.composables.loading.CircularLoading
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
    imageMaxHeight: Dp
) {
    val pagerState = rememberPagerState(pageCount = { imageData.size })

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
                if (imageData.size >= page) {
                    Image(
                        modifier = Modifier
                            .clickable(onClick = { onImageClick() }),
                        bitmap = imageData[page].image,
                        contentDescription = "",
                    )
                }
            }
        }

        // Next image button
        IconButton(
            onClick = {
                coroutineScope.launch {
                    if (pagerState.currentPage + 1 <= imageData.size) pagerState.animateScrollToPage(pagerState.currentPage + 1)
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