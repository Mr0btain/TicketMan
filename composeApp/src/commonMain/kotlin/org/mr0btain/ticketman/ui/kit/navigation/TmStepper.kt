package org.mr0btain.ticketman.ui.kit.navigation

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.buttons.TM_DISABLED_ALPHA
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.check

/**
 * Numbered steps of a form that's filled in order, like checking in a device
 * @param canOpen Whether a step can be jumped to yet, usually once the ones before it are filled in
 */
@Composable
fun TmStepper(
    steps: List<String>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    canOpen: (Int) -> Boolean = { it <= currentIndex }
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val done = index < currentIndex
            val current = index == currentIndex
            val reachable = canOpen(index)
            val circle by animateColorAsState(
                when {
                    current -> colors.accent.color
                    done -> colors.accent.soft
                    else -> colors.surfaceSunken
                }
            )

            if (index > 0) {
                Box(
                    Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(if (done || current) colors.accent.color.copy(alpha = 0.5f) else colors.outline)
                )
            }

            // On a narrow screen the row scrolls, so keep the step being worked on in sight
            val bringIntoView = remember { BringIntoViewRequester() }
            if (current) LaunchedEffect(index) { bringIntoView.bringIntoView() }

            Row(
                modifier = Modifier
                    .bringIntoViewRequester(bringIntoView)
                    .alpha(if (reachable || current) 1f else TM_DISABLED_ALPHA)
                    .tmClickable(onClick = { onSelect(index) }, shape = TmTheme.shapes.pill, enabled = reachable && !current, role = Role.Tab)
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(circle, TmTheme.shapes.pill)
                        .border(1.dp, if (current || done) circle else colors.outline, TmTheme.shapes.pill),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) {
                        Icon(
                            modifier = Modifier.size(14.dp),
                            painter = painterResource(Res.drawable.check),
                            contentDescription = "Done",
                            tint = colors.accent.color
                        )
                    } else {
                        Text(
                            text = (index + 1).toString(),
                            style = TmTheme.typography.caption,
                            color = if (current) colors.accent.onColor else colors.textMuted
                        )
                    }
                }

                Text(
                    text = step,
                    style = TmTheme.typography.label,
                    color = if (current) colors.text else colors.textMuted
                )
            }
        }
    }
}
