package org.mr0btain.ticketman.ui.composables.scrollbar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp

/** Contains the default values used by ScrollBars */
object ScrollBarDefaults {
    private val ScrollBarHorizontalPadding = 4.dp
    private val ScrollBarVerticalPadding = 4.dp

    /** The default content padding used by ScrollBars */
    val ContentPadding =
        PaddingValues(
            start = ScrollBarHorizontalPadding,
            top = ScrollBarVerticalPadding,
            end = ScrollBarHorizontalPadding,
            bottom = ScrollBarVerticalPadding,
        )

    /**
     * Which side the scroll bar should be on. True means right for Vertical scroll bars
     * Defaults to right
     */

    val scrollBarOnRight = true

    /**
     * Which side the scroll bar should be on. True means Top for Horizontal scroll bars
     * Defaults to bottom
     */

    val scrollBarOnTop = false

    /**
     * Whether we should clip the scroll bar to the content or the edge of the parent
     */

    val clipToContent = true

    /**
     * The default size of the scroll bar be it width or height depending on whether its vertical or horizonal
     */
    val size = 8.dp

    /**
     * Creates a scrollBarColors that represents the default background and content colors used in a
     * ScrollBar.
     *
     * @param trackColor the track color of this ScrollBar when enabled
     * @param barColor the bar color of this ScrollBar when enabled
     * @param disabledTrackColor the track color of this ScrollBar when not enabled
     * @param disabledBarColor the bar color of this ScrollBar when not enabled
     */
    @Composable
    fun scrollBarColors(
        trackColor: Color = MaterialTheme.colorScheme.outline,
        barColor: Color = contentColorFor(trackColor),
        disabledTrackColor: Color =
            MaterialTheme.colorScheme.outlineVariant
                .copy(alpha = 0.12f)
                .compositeOver(MaterialTheme.colorScheme.outline),
        disabledBarColor: Color =
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
    ): ScrollBarColors =
        DefaultScrollBarColors(
            trackColor = trackColor,
            barColor = barColor,
            disabledTrackColor = disabledTrackColor,
            disabledBarColor = disabledBarColor
        )
}


/**
 * Represents the background and content colors used in a scroll bar in different states.
 */
@Stable
interface ScrollBarColors {
    /**
     * Represents the background color for this track, depending on [enabled].
     *
     * @param enabled whether the scroll bar is enabled
     */
    @Composable fun trackColor(enabled: Boolean): State<Color>
    /**
     * Represents the content color for this scroll bar, depending on [enabled].
     *
     * @param enabled whether the scroll bar is enabled
     */
    @Composable fun barColor(enabled: Boolean): State<Color>
}

/** Default [ScrollBarColors] implementation. */
@Immutable
private class DefaultScrollBarColors(
    private val trackColor: Color,
    private val barColor: Color,
    private val disabledTrackColor: Color,
    private val disabledBarColor: Color,
) : ScrollBarColors {
    @Composable
    override fun trackColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) trackColor else disabledTrackColor)
    }

    @Composable
    override fun barColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) barColor else disabledBarColor)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as DefaultScrollBarColors
        if (trackColor != other.trackColor) return false
        if (barColor != other.barColor) return false
        if (disabledTrackColor != other.disabledTrackColor) return false
        if (disabledBarColor != other.disabledBarColor) return false
        return true
    }

    override fun hashCode(): Int {
        var result = trackColor.hashCode()
        result = 31 * result + barColor.hashCode()
        result = 31 * result + disabledTrackColor.hashCode()
        result = 31 * result + disabledBarColor.hashCode()
        return result
    }
}