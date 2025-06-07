package org.mr0btain.ticketman.ui.composables

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import org.mr0btain.ticketman.ui.composables.TabColumnDefaults.Divider
import org.mr0btain.ticketman.ui.composables.TabColumnDefaults.Indicator
import org.mr0btain.ticketman.ui.composables.TabColumnDefaults.tabIndicatorOffset

/**
 * [Material Design fixed tabs](https://material.io/components/tabs#fixed-tabs)
 *
 * Fixed tabs display all tabs in a set simultaneously. They are best for switching between related
 * content quickly, such as between transportation methods in a map. To navigate between fixed tabs,
 * tap an individual tab, or swipe left or right in the content area.
 *
 * ![Fixed tabs
 * image](https://developer.android.com/images/reference/androidx/compose/material/fixed-tabs.png)
 *
 * A TabRow contains a row of [Tab]s, and displays an indicator underneath the currently selected
 * tab. A TabRow places its tabs evenly spaced along the entire row, with each tab taking up an
 * equal amount of space
 *
 * As well as customizing the tab, you can also provide a custom [indicator], to customize the
 * indicator displayed for a tab. [indicator] will be placed to fill the entire TabRow, so it should
 * internally take care of sizing and positioning the indicator to match changes to
 * [selectedTabIndex].
 *
 * For example, given an indicator that draws a rounded rectangle near the edges of the [Tab]:
 *
 * @sample androidx.compose.material.samples.FancyIndicator
 *
 * We can reuse [TabColumnDefaults.tabIndicatorOffset] and just provide this indicator, as we aren't
 * changing how the size and position of the indicator changes between tabs:
 *
 * @sample androidx.compose.material.samples.FancyIndicatorTabs
 *
 * You may also want to use a custom transition, to allow you to dynamically change the appearance
 * of the indicator as it animates between tabs, such as changing its color or size. [indicator] is
 * stacked on top of the entire TabRow, so you just need to provide a custom transition that
 * animates the offset of the indicator from the start of the TabColumn. For example, take the
 * following example that uses a transition to animate the offset, width, and color of the same
 * FancyIndicator from before, also adding a physics based 'spring' effect to the indicator in the
 * direction of motion:
 *
 * @param selectedTabIndex the index of the currently selected tab
 * @param modifier optional [Modifier] for this TabRow
 * @param backgroundColor The background color for the TabRow. Use [Color.Transparent] to have no
 *   color.
 * @param contentColor The preferred content color provided by this TabRow to its children. Defaults
 *   to either the matching content color for [backgroundColor], or if [backgroundColor] is not a
 *   color from the theme, this will keep the same value set above this TabRow.
 * @param indicator the indicator that represents which tab is currently selected. By default this
 *   will be a [TabColumnDefaults.Indicator], using a [TabColumnDefaults.tabIndicatorOffset] modifier to
 *   animate its position. Note that this indicator will be forced to fill up the entire TabRow, so
 *   you should use [TabColumnDefaults.tabIndicatorOffset] or similar to animate the actual drawn
 *   indicator inside this space, and provide an offset from the start.
 * @param divider the divider displayed at the bottom of the TabRow. This provides a layer of
 *   separation between the TabRow and the content displayed underneath.
 * @param tabs the tabs inside this TabRow. Typically this will be multiple [Tab]s. Each element
 *   inside this lambda will be measured and placed evenly across the TabRow, each taking up equal
 *   space.
 */
@Composable
@UiComposable
fun TabColumn(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    indicatorLeft: Boolean = false,
    indicator: @Composable @UiComposable (tabPositions: List<TabPosition>) -> Unit =
        @Composable { tabPositions ->
            Indicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex], indicatorLeft))
        },
    divider: @Composable @UiComposable () -> Unit = @Composable { Divider() },
    tabs: @Composable @UiComposable () -> Unit,
) {
    Surface(
        modifier = modifier.selectableGroup(),
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        SubcomposeLayout(Modifier.fillMaxHeight()) { constraints ->
            val tabColumnHeight = constraints.maxHeight
            val tabMeasurables = subcompose(TabSlots.Tabs, tabs)
            val tabCount = tabMeasurables.size
            val tabHeight = (tabColumnHeight / tabCount)
            val tabPlaceables =
                tabMeasurables.fastMap {
                    it.measure(constraints.copy(minHeight = 0, maxHeight = tabHeight))
                }

            val tabColumnWidth = tabPlaceables.fastMaxBy { it.width }?.width ?: 0

            val tabPositions =
                List(tabCount) { index -> TabPosition(tabHeight.toDp() * index, tabHeight.toDp()) }

            layout(tabColumnWidth, tabColumnHeight) {
                tabPlaceables.fastForEachIndexed { index, placeable ->
                    placeable.placeRelative(0, index * tabHeight)
                }

                subcompose(TabSlots.Divider, divider).fastForEach {
                    val placeable = it.measure(constraints.copy(minWidth = 0))
                    placeable.placeRelative(if (indicatorLeft) tabColumnWidth - placeable.width else tabColumnWidth + placeable.width, 0)
                }

                subcompose(TabSlots.Indicator) { indicator(tabPositions) }
                    .fastForEach {
                        it.measure(Constraints.fixed(tabColumnWidth, tabColumnHeight)).placeRelative(0, 0)
                    }
            }
        }
    }
}

@Immutable
class TabPosition internal constructor(val bottom: Dp, val height: Dp) {
    val right: Dp
        get() = bottom + height

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabPosition) return false

        if (bottom != other.bottom) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bottom.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }

    override fun toString(): String {
        return "TabPosition(bottom=$bottom, right=$right, height=$height)"
    }
}

/** Contains default implementations and values used for TabRow. */
object TabColumnDefaults {
    /**
     * Default [Divider], which will be positioned at the bottom of the [TabRow], underneath the
     * indicator.
     *
     * @param modifier modifier for the divider's layout
     * @param thickness thickness of the divider
     * @param color color of the divider
     */
    @Composable
    fun Divider(
        modifier: Modifier = Modifier,
        thickness: Dp = DividerThickness,
        color: Color = LocalContentColor.current.copy(alpha = DividerOpacity),
    ) {
        VerticalDivider(modifier = modifier, thickness = thickness, color = color)
    }

    /**
     * Default indicator, which will be positioned at the bottom of the [TabColumn], on top of the
     * divider.
     *
     * @param modifier modifier for the indicator's layout
     * @param width height of the indicator
     * @param color color of the indicator
     */
    @Composable
    fun Indicator(
        modifier: Modifier = Modifier,
        width: Dp = IndicatorWidth,
        color: Color = MaterialTheme.colorScheme.primary  // LocalContentColor.current
    ) {
        Box(modifier.fillMaxHeight().width(width).background(color = color))
    }

    /**
     * [Modifier] that takes up all the available width inside the [TabColumn], and then animates the
     * offset of the indicator it is applied to, depending on the [currentTabPosition].
     *
     * @param currentTabPosition [TabPosition] of the currently selected tab. This is used to
     *   calculate the offset of the indicator this modifier is applied to, as well as its height.
     */
    fun Modifier.tabIndicatorOffset(
        currentTabPosition: TabPosition,
        leftSide: Boolean
    ): Modifier =
        composed(
            inspectorInfo =
                debugInspectorInfo {
                    name = "tabIndicatorOffset"
                    value = currentTabPosition
                }
        ) {
            val currentTabHeight by
            animateDpAsState(
                targetValue = currentTabPosition.height,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
            val indicatorOffset by
            animateDpAsState(
                targetValue = currentTabPosition.bottom,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
            fillMaxWidth()
                .wrapContentSize(if (leftSide) Alignment.TopStart else Alignment.TopEnd)
                .offset { IntOffset(x = 0, y = indicatorOffset.roundToPx()) }
                .height(currentTabHeight)
        }

    /** Default opacity for the color of [Divider] */
    const val DividerOpacity = 0.12f

    /** Default thickness for [Divider] */
    val DividerThickness = 1.dp

    /** Default height for [Indicator] */
    val IndicatorWidth = 3.dp
}

private enum class TabSlots {
    Tabs,
    Divider,
    Indicator,
}