package org.mr0btain.ticketman.ui.kit.data

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.utils.ImageUtils
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.ui.dialog.FullScreenDialog
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.chevron_left
import ticketman.composeapp.generated.resources.chevron_right
import ticketman.composeapp.generated.resources.image

/**
 * Photos side by side, swiped or stepped through with the arrows. Photos still loading show a placeholder
 * @param clickEnabled Lets a photo be clicked, usually to open it full size
 */
@Composable
fun ImagePager(
    modifier: Modifier = Modifier,
    storedImages: List<StoredImage>,
    onImageClick: () -> Unit = {},
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    clickEnabled: Boolean = true
) {
    val pagerState = rememberPagerState(pageCount = { storedImages.size })
    val bitmaps = remember { mutableStateMapOf<StoredImage, ImageBitmap>() }
    var pageCount by remember { mutableIntStateOf(storedImages.size) }

    LaunchedEffect(storedImages) {
        storedImages.filter { it !in bitmaps }.forEach { image ->
            ImageUtils.getImage(image, true)?.let { bitmaps[image] = it }
        }
    }

    Box(modifier) {
        HorizontalPager(
            modifier = Modifier.padding(TmTheme.spacing.sm),
            state = pagerState,
            snapPosition = SnapPosition.Center
        ) { page ->
            val bitmap = storedImages.getOrNull(page)?.let { bitmaps[it] }
            val shape = TmTheme.shapes.panel

            Box(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        modifier = Modifier
                            .clip(shape)
                            .tmClickable(onClick = onImageClick, shape = shape, enabled = clickEnabled),
                        bitmap = bitmap,
                        contentDescription = "Photo ${page + 1} of ${storedImages.size}",
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(TmTheme.colors.surfaceSunken, shape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = Modifier.size(28.dp),
                            painter = painterResource(Res.drawable.image),
                            contentDescription = "Photo loading",
                            tint = TmTheme.colors.textFaint
                        )
                    }
                }
            }
        }

        TmIconButton(
            modifier = Modifier.align(Alignment.CenterStart),
            icon = Res.drawable.chevron_left,
            contentDescription = "Previous photo",
            variant = TmButtonVariant.SECONDARY,
            enabled = pagerState.currentPage > 0,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
        )

        TmIconButton(
            modifier = Modifier.align(Alignment.CenterEnd),
            icon = Res.drawable.chevron_right,
            contentDescription = "Next photo",
            variant = TmButtonVariant.SECONDARY,
            enabled = pagerState.currentPage < storedImages.size - 1,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
        )
    }

    // Jump to a photo that was just added, or back to the start when one is removed
    LaunchedEffect(storedImages.size) {
        if (storedImages.size > pageCount) {
            pagerState.scrollToPage(storedImages.size - 1)
        } else if (storedImages.size < pageCount) {
            pagerState.scrollToPage(0)
        }
        pageCount = storedImages.size
    }
}

/** Photos shown big over everything, clicking outside closes it */
@Composable
fun ImagePopup(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    isVisible: Boolean,
    images: List<StoredImage>
) {
    if (isVisible && images.isNotEmpty()) {
        FullScreenDialog(modifier = modifier, onDismiss = onDismiss) {
            ImagePager(storedImages = images, clickEnabled = false)
        }
    }
}
