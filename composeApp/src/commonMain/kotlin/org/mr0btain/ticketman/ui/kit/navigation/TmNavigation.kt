package org.mr0btain.ticketman.ui.kit.navigation

import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.ui.kit.foundation.TmGlassSurface
import org.mr0btain.ticketman.ui.kit.foundation.tmClickable
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * A place in the app for the nav rail and bottom nav
 * @param key Tells the items apart, like a route
 */
@Immutable
data class TmNavItem(
    val key: String,
    val label: String,
    val icon: DrawableResource
)

/**
 * Glass side rail for wide screens
 * @param expanded Shows labels next to the icons
 * @param header Goes above the items, like a menu button
 * @param footer Pinned to the bottom, like the signed in user
 */
@Composable
fun TmNavRail(
    items: List<TmNavItem>,
    selectedKey: String?,
    onSelect: (TmNavItem) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    header: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {}
) {
    val spacing = TmTheme.spacing

    TmGlassSurface(
        modifier = modifier
            .fillMaxHeight()
            .width(if (expanded) 200.dp else 64.dp),
        shape = TmTheme.shapes.panel
    ) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally
        ) {
            header()

            items.forEach { item ->
                RailItem(item = item, selected = item.key == selectedKey, expanded = expanded, onClick = { onSelect(item) })
            }

            Spacer(Modifier.weight(1f))

            footer()
        }
    }
}

@Composable
private fun RailItem(item: TmNavItem, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val colors = TmTheme.colors
    val shape = TmTheme.shapes.control
    val fill by animateColorAsState(if (selected) colors.accent.soft else Color.Transparent)
    val content = if (selected) colors.accent.color else colors.textMuted

    Row(
        modifier = Modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.size(48.dp))
            .heightIn(min = 48.dp)
            .background(fill, shape)
            .tmClickable(onClick = onClick, shape = shape, role = Role.Tab, overlay = content)
            .padding(horizontal = if (expanded) TmTheme.spacing.md else 0.dp),
        horizontalArrangement = if (expanded) Arrangement.spacedBy(TmTheme.spacing.md) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(item.icon),
            contentDescription = if (expanded) null else item.label,
            tint = content
        )

        if (expanded) {
            Text(
                text = item.label,
                style = TmTheme.typography.label,
                color = if (selected) colors.text else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Glass bar across the top of a screen
 * @param navigation Goes before the title, like a back or menu button
 */
@Composable
fun TmTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    TmGlassSurface(modifier = modifier.fillMaxWidth(), shape = TmTheme.shapes.panel) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .padding(horizontal = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigation?.invoke()

            Column(Modifier.weight(1f)) {
                Text(text = title, style = TmTheme.typography.subtitle, color = colors.text, maxLines = 1)
                if (subtitle != null) {
                    Text(text = subtitle, style = TmTheme.typography.caption, color = colors.textMuted, maxLines = 1)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

/** Glass bar of places along the bottom, for phones */
@Composable
fun TmBottomNav(
    items: List<TmNavItem>,
    selectedKey: String?,
    onSelect: (TmNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TmTheme.colors

    TmGlassSurface(modifier = modifier.fillMaxWidth(), shape = TmTheme.shapes.panel) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .padding(horizontal = TmTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val content = if (selected) colors.accent.color else colors.textMuted

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .tmClickable(onClick = { onSelect(item) }, shape = TmTheme.shapes.control, role = Role.Tab)
                        .padding(vertical = TmTheme.spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (selected) colors.accent.soft else Color.Transparent, TmTheme.shapes.pill)
                            .padding(horizontal = TmTheme.spacing.md, vertical = TmTheme.spacing.xxs)
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(item.icon),
                            contentDescription = null,
                            tint = content
                        )
                    }

                    Text(text = item.label, style = TmTheme.typography.caption, color = content, maxLines = 1)
                }
            }
        }
    }
}
