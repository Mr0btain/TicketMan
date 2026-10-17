package org.mr0btain.ticketman.ui.kit.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.foundation.GlassSurface
import org.mr0btain.ticketman.ui.kit.foundation.TmControlSize
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.kit.theme.TmTone
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.alert_circle
import ticketman.composeapp.generated.resources.alert_triangle
import ticketman.composeapp.generated.resources.check_circle
import ticketman.composeapp.generated.resources.info

/**
 * Glass snackbar host, put it at the bottom of the screen.
 * Messages should say what happened ("Ticket saved"), not apologize
 */
@Composable
fun TmSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState = state, modifier = modifier.padding(TmTheme.spacing.xl)) { data ->
        TmSnackbarContent(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            onAction = { data.performAction() }
        )
    }
}

/** What a snackbar looks like, used by [TmSnackbarHost] */
@Composable
fun TmSnackbarContent(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    val shape = TmTheme.shapes.control

    GlassSurface(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .shadow(16.dp, shape, clip = false),
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(start = TmTheme.spacing.lg, end = TmTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f, fill = false).padding(vertical = TmTheme.spacing.md),
                text = message,
                style = TmTheme.typography.body,
                color = TmTheme.colors.text
            )

            actionLabel?.let { label ->
                TmButton(
                    text = label,
                    onClick = onAction,
                    variant = TmButtonVariant.GHOST,
                    size = TmControlSize.SMALL
                )
            }
        }
    }
}

/** Kinds of [TmNotice], which picks the color and icon */
enum class TmNoticeKind { INFO, SUCCESS, WARNING, ERROR }

/**
 * Message box inside a screen or dialog, like a failed save or a warning before checkout.
 * Say what happened and what to do about it
 */
@Composable
fun TmNotice(
    text: String,
    modifier: Modifier = Modifier,
    kind: TmNoticeKind = TmNoticeKind.INFO,
    title: String? = null
) {
    val colors = TmTheme.colors
    val (tone: TmTone, icon: DrawableResource) = when (kind) {
        TmNoticeKind.INFO -> colors.accent to Res.drawable.info
        TmNoticeKind.SUCCESS -> colors.success to Res.drawable.check_circle
        TmNoticeKind.WARNING -> colors.warning to Res.drawable.alert_triangle
        TmNoticeKind.ERROR -> colors.danger to Res.drawable.alert_circle
    }
    val shape = TmTheme.shapes.control

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tone.soft, shape)
            .border(1.dp, tone.color.copy(alpha = 0.3f), shape)
            .padding(TmTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)
    ) {
        Icon(
            modifier = Modifier.size(18.dp),
            painter = painterResource(icon),
            contentDescription = null,
            tint = tone.color
        )

        Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
            if (title != null) {
                Text(text = title, style = TmTheme.typography.label, color = colors.text)
            }
            Text(text = text, style = TmTheme.typography.body, color = if (title != null) colors.textMuted else colors.text)
        }
    }
}

/**
 * Shown in place of a list with nothing in it. Use [actionText] to offer the next step
 */
@Composable
fun TmEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: DrawableResource? = null,
    actionText: String? = null,
    onAction: () -> Unit = {}
) {
    val colors = TmTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(TmTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(28.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.textFaint
            )
        }

        Text(text = title, style = TmTheme.typography.subtitle, color = colors.text, textAlign = TextAlign.Center)

        if (message != null) {
            Text(
                modifier = Modifier.widthIn(max = 360.dp),
                text = message,
                style = TmTheme.typography.body,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )
        }

        if (actionText != null) {
            TmButton(
                modifier = Modifier.padding(top = TmTheme.spacing.sm),
                text = actionText,
                onClick = onAction,
                variant = TmButtonVariant.SECONDARY
            )
        }
    }
}

/** Spinner with an optional line of text under it, for whole panes that are loading */
@Composable
fun TmLoading(
    modifier: Modifier = Modifier,
    text: String? = null
) {
    Column(
        modifier = modifier.padding(TmTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.md)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = TmTheme.colors.accent.color,
            trackColor = TmTheme.colors.hairline,
            strokeWidth = 3.dp
        )

        if (text != null) {
            Text(text = text, style = TmTheme.typography.body, color = TmTheme.colors.textMuted)
        }
    }
}

/** Thin bar that runs while something loads with no known end, like a search */
@Composable
fun TmProgressBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth().height(3.dp),
        color = TmTheme.colors.accent.color,
        trackColor = TmTheme.colors.hairline
    )
}
