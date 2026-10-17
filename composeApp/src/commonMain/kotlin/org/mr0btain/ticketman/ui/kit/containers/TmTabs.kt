package org.mr0btain.ticketman.ui.kit.containers

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.ui.kit.foundation.TmDivider
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Tabs with an accent line under the open one
 * @param badges Optional count shown next to a tab's name, by index
 */
@Composable
fun TmTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<Int, Int> = emptyMap()
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                val line by animateColorAsState(if (isSelected) colors.accent.color else Color.Transparent)

                Column(
                    modifier = Modifier
                        .tmClickable(onClick = { onSelect(index) }, shape = TmTheme.shapes.chip, role = Role.Tab),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab,
                            style = TmTheme.typography.label,
                            color = if (isSelected) colors.text else colors.textMuted
                        )

                        badges[index]?.let { count ->
                            Box(
                                Modifier
                                    .background(if (isSelected) colors.accent.soft else colors.surfaceRaised, TmTheme.shapes.pill)
                                    .padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = TmTheme.typography.caption,
                                    color = if (isSelected) colors.accent.color else colors.textMuted
                                )
                            }
                        }
                    }

                    Box(
                        Modifier
                            .padding(horizontal = spacing.sm)
                            .height(2.dp)
                            .width(24.dp)
                            .background(line, TmTheme.shapes.pill)
                    )
                }
            }
        }

        TmDivider()
    }
}
