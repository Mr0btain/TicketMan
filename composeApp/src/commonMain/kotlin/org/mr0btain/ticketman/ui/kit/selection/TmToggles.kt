package org.mr0btain.ticketman.ui.kit.selection

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.buttons.TM_DISABLED_ALPHA
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.check

/** On/off switch. Put it in a TmListRow when it needs a label */
@Composable
fun TmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val colors = TmTheme.colors
    val shape = TmTheme.shapes.pill
    val track by animateColorAsState(if (checked) colors.accent.color else colors.surfaceSunken)
    val thumb by animateColorAsState(if (checked) colors.accent.onColor else colors.textMuted)
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 0.dp)

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else TM_DISABLED_ALPHA)
            .size(width = 42.dp, height = 24.dp)
            .background(track, shape)
            .border(1.dp, if (checked) track else colors.outline, shape)
            .tmClickable(onClick = { onCheckedChange(!checked) }, shape = shape, enabled = enabled, role = Role.Switch)
            .semantics {
                stateDescription = if (checked) "On" else "Off"
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .background(thumb, shape)
        )
    }
}

/** Checkbox with an optional label to its right, clicking the label toggles it too */
@Composable
fun TmCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val colors = TmTheme.colors
    val boxShape = TmTheme.shapes.chip
    val fill by animateColorAsState(if (checked) colors.accent.color else colors.surfaceSunken)

    ToggleRow(
        modifier = modifier,
        label = label,
        enabled = enabled,
        role = Role.Checkbox,
        onClick = { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(fill, boxShape)
                .border(1.dp, if (checked) fill else colors.outline, boxShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    modifier = Modifier.size(14.dp),
                    painter = painterResource(Res.drawable.check),
                    contentDescription = null,
                    tint = colors.accent.onColor
                )
            }
        }
    }
}

/** One option of a group where only one can be picked */
@Composable
fun TmRadio(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true
) {
    val colors = TmTheme.colors
    val ring by animateColorAsState(if (selected) colors.accent.color else colors.outline)

    ToggleRow(modifier = modifier, label = label, enabled = enabled, role = Role.RadioButton, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(colors.surfaceSunken, TmTheme.shapes.pill)
                .border(if (selected) 5.dp else 1.dp, ring, TmTheme.shapes.pill)
        )
    }
}

@Composable
private fun ToggleRow(
    modifier: Modifier,
    label: String?,
    enabled: Boolean,
    role: Role,
    onClick: () -> Unit,
    control: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else TM_DISABLED_ALPHA)
            .tmClickable(onClick = onClick, shape = TmTheme.shapes.chip, enabled = enabled, role = role)
            .padding(TmTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        control()

        if (label != null) {
            Text(text = label, style = TmTheme.typography.body, color = TmTheme.colors.text)
        }
    }
}
