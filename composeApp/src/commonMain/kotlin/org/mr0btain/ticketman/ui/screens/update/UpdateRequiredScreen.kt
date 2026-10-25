package org.mr0btain.ticketman.ui.screens.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.mr0btain.ticketman.components.network.VersionNotice
import org.mr0btain.ticketman.ui.kit.buttons.TmButton
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.data.TmKeyValueRow
import org.mr0btain.ticketman.ui.kit.foundation.TmBackdrop
import org.mr0btain.ticketman.ui.kit.foundation.TmSurface
import org.mr0btain.ticketman.ui.kit.theme.TmTheme
import org.mr0btain.ticketman.ui.screens.navigation.shellInsets
import ticketman.composeapp.generated.resources.Res
import ticketman.composeapp.generated.resources.alert_octagon
import ticketman.composeapp.generated.resources.refresh_cw
import ticketman.composeapp.generated.resources.server

/**
 * Takes the whole app while the server is a major version away from it, since breaking changes between them mean
 * nothing can be loaded. Says which one is behind and what to update it to
 * @param retrying Whether [onRetry] is connecting right now
 * @param onRetry Tries the server again, once it or this app was updated
 * @param onChangeServer Puts this away to type another address, null where that can't be done (a kiosk)
 */
@Composable
fun UpdateRequiredScreen(
    notice: VersionNotice,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retrying: Boolean = false,
    onChangeServer: (() -> Unit)? = null
) {
    val colors = TmTheme.colors
    val spacing = TmTheme.spacing

    TmBackdrop(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(shellInsets)
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            TmSurface(modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(Res.drawable.alert_octagon),
                        contentDescription = null,
                        tint = colors.danger.color
                    )

                    Text(
                        text = if (notice.updateServer) "Update the server" else "Update this app",
                        style = TmTheme.typography.title,
                        color = colors.text
                    )

                    Text(text = notice.message, style = TmTheme.typography.body, color = colors.textMuted)

                    Column {
                        TmKeyValueRow(label = "This app", value = notice.appVersion, numeric = true)
                        TmKeyValueRow(label = "Server", value = notice.serverVersion ?: "Unknown", numeric = true)
                    }

                    Text(
                        text = "Records stay on the server and nothing is lost. Once both are on the same major " +
                                "version, try again.",
                        style = TmTheme.typography.caption,
                        color = colors.textFaint
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End)
                    ) {
                        onChangeServer?.let { change ->
                            TmButton(
                                text = "Change server",
                                onClick = change,
                                variant = TmButtonVariant.SECONDARY,
                                leadingIcon = Res.drawable.server
                            )
                        }

                        TmButton(
                            text = "Try again",
                            onClick = onRetry,
                            loading = retrying,
                            leadingIcon = Res.drawable.refresh_cw
                        )
                    }
                }
            }
        }
    }
}
