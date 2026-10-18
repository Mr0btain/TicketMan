package org.mr0btain.ticketman.ui.kit.data

import org.mr0btain.ticketman.ui.kit.containers.TmFullScreenDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.image.LocalImageClient
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.buttons.TmIconButton
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.alert_circle
import ticketman.composeapp.generated.resources.chevron_left
import ticketman.composeapp.generated.resources.chevron_right
import ticketman.composeapp.generated.resources.image
import ticketman.composeapp.generated.resources.maximize_2
import ticketman.composeapp.generated.resources.minimize_2
import ticketman.composeapp.generated.resources.trash_2

/**
 * One photo from the server, loaded through the cache. Shows a placeholder while it loads, and when there's no
 * server (previews, render tests) or it couldn't be fetched
 * @param variant Which size to fetch, pick the smallest that looks sharp where it's shown
 * @param keepAspectRatio Sizes itself to the photo's shape, known before it loads
 */
@Composable
fun TmImage(
    image: StoredImage,
    modifier: Modifier = Modifier,
    variant: ImageVariant = ImageVariant.THUMB,
    contentDescription: String? = image.description.ifBlank { null },
    contentScale: ContentScale = ContentScale.Fit,
    keepAspectRatio: Boolean = false
) {
    val url = LocalImageClient.current?.urlFor(image, variant)
    val shape = TmTheme.shapes.panel
    val sized = image.aspectRatio
        ?.takeIf { keepAspectRatio }
        ?.let { modifier.aspectRatio(it) } ?: modifier

    if (url == null) {
        ImagePlaceholder(sized, failed = false)
        return
    }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(url)
            .build()
    )
    val state by painter.state.collectAsState()

    Box(sized, contentAlignment = Alignment.Center) {
        // Always drawn so the painter knows its size and starts loading
        Image(
            modifier = Modifier.fillMaxSize().clip(shape),
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale
        )

        when (state) {
            is AsyncImagePainter.State.Success -> Unit
            is AsyncImagePainter.State.Error -> ImagePlaceholder(Modifier.fillMaxSize(), failed = true)
            else -> ImagePlaceholder(Modifier.fillMaxSize(), failed = false)
        }
    }
}

@Composable
private fun ImagePlaceholder(modifier: Modifier, failed: Boolean) {
    Box(
        modifier.background(TmTheme.colors.surfaceSunken, TmTheme.shapes.panel),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(28.dp),
            painter = painterResource(if (failed) Res.drawable.alert_circle else Res.drawable.image),
            contentDescription = if (failed) "Photo couldn't be loaded" else "Photo loading",
            tint = TmTheme.colors.textFaint
        )
    }
}

/**
 * Photos side by side, swiped or stepped through with the arrows
 * @param variant Size fetched, thumbnails for small strips and display for anything bigger
 * @param onImageClick Gets the page of the photo clicked
 * @param clickEnabled Lets a photo be clicked, usually to open it full size
 * @param onRemove Shows a remove button on the photo in view, null for none
 * @param pagerState Pass one in to move between photos from outside, like the arrow keys in [TmImageView]
 */
@Composable
fun TmImagePager(
    modifier: Modifier = Modifier,
    storedImages: List<StoredImage>,
    variant: ImageVariant = ImageVariant.THUMB,
    onImageClick: (page: Int) -> Unit = {},
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    clickEnabled: Boolean = true,
    onRemove: ((StoredImage) -> Unit)? = null,
    pagerState: PagerState = rememberPagerState(pageCount = { storedImages.size })
) {
    var pageCount by remember { mutableIntStateOf(storedImages.size) }

    Box(modifier) {
        HorizontalPager(
            modifier = Modifier.padding(TmTheme.spacing.sm),
            state = pagerState,
            snapPosition = SnapPosition.Center,
            key = { page -> storedImages.getOrNull(page)?.identifier?.toString() ?: page }
        ) { page ->
            val image = storedImages.getOrNull(page) ?: return@HorizontalPager
            val shape = TmTheme.shapes.panel

            Box(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                TmImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .tmClickable(onClick = { onImageClick(page) }, shape = shape, enabled = clickEnabled),
                    image = image,
                    variant = variant,
                    contentDescription = image.description.ifBlank { "Photo ${page + 1} of ${storedImages.size}" }
                )
            }
        }

        if (onRemove != null) {
            storedImages.getOrNull(pagerState.currentPage)?.let { image ->
                TmIconButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    icon = Res.drawable.trash_2,
                    contentDescription = "Remove photo",
                    variant = TmButtonVariant.SECONDARY,
                    onClick = { onRemove(image) }
                )
            }
        }

        if (storedImages.size > 1) {
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

/**
 * Photos shown big over everything, closed like any [TmFullScreenDialog]. The left and right arrow keys step
 * through them. Shows the display size, with a button to fetch
 * the sharper original (up to 2560px) when the detail matters on a big screen
 * @param startPage Photo shown first, usually the one clicked to open it
 */
@Composable
fun TmImageView(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    isVisible: Boolean,
    images: List<StoredImage>,
    startPage: Int = 0
) {
    if (isVisible && images.isNotEmpty()) {
        var fullSize by remember { mutableStateOf(false) }
        val pagerState = rememberPagerState(
            initialPage = startPage.coerceIn(0, images.size - 1),
            pageCount = { images.size }
        )
        val coroutineScope = rememberCoroutineScope()

        fun step(by: Int): Boolean {
            val target = (pagerState.currentPage + by).coerceIn(0, images.size - 1)
            if (target != pagerState.currentPage) coroutineScope.launch { pagerState.animateScrollToPage(target) }
            return true
        }

        TmFullScreenDialog(
            modifier = modifier,
            onDismiss = onDismiss,
            onKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@TmFullScreenDialog false

                when (event.key) {
                    Key.DirectionLeft -> step(-1)
                    Key.DirectionRight -> step(1)
                    else -> false
                }
            }
        ) {
            Column(Modifier.fillMaxSize()) {
                TmImagePager(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    storedImages = images,
                    variant = if (fullSize) ImageVariant.ORIGINAL else ImageVariant.DISPLAY,
                    coroutineScope = coroutineScope,
                    clickEnabled = false,
                    pagerState = pagerState
                )

                TmButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(TmTheme.spacing.sm),
                    text = if (fullSize) "Show smaller" else "Show original",
                    leadingIcon = if (fullSize) Res.drawable.minimize_2 else Res.drawable.maximize_2,
                    variant = TmButtonVariant.GHOST,
                    size = TmControlSize.SMALL,
                    onClick = { fullSize = !fullSize }
                )
            }
        }
    }
}
