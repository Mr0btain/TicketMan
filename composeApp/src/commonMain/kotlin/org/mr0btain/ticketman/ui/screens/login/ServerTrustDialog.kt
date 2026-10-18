package org.mr0btain.ticketman.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.mr0btain.ticketman.components.network.UntrustedCertificate
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonRow
import org.mr0btain.ticketman.ui.kit.buttons.TmButtonVariant
import org.mr0btain.ticketman.ui.kit.containers.TmDialog
import org.mr0btain.ticketman.ui.kit.feedback.TmNotice
import org.mr0btain.ticketman.ui.kit.feedback.TmNoticeKind
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Asks whether to trust a server that made its own certificate, after checking its fingerprint against the one on
 * the server. A changed certificate gets a warning, since it can mean something is pretending to be the server
 */
@Composable
fun ServerTrustDialog(
    certificate: UntrustedCertificate,
    onTrust: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = TmTheme.colors

    TmDialog(
        title = if (certificate.changed) "This server's certificate changed" else "Check this server",
        description = "${certificate.address} made its own certificate, so this device can't tell it's the shop's " +
                "server on its own. Check the code below matches the one on the server's admin screen (Admin, Server) " +
                "or in its log.",
        onDismissRequest = onDismiss,
        dismissOnOutsideClick = false,
        maxWidth = 520.dp,
        actions = {
            TmButtonRow(
                confirmText = if (certificate.changed) "Trust the new one" else "It matches, connect",
                confirmVariant = if (certificate.changed) TmButtonVariant.DANGER else TmButtonVariant.PRIMARY,
                cancelText = "Don't connect",
                onConfirm = onTrust,
                onCancel = onDismiss
            )
        }
    ) {
        if (certificate.changed) {
            TmNotice(
                kind = TmNoticeKind.WARNING,
                text = "Only trust it if the server's certificate was replaced on purpose, like after moving it to a " +
                        "new machine. Otherwise something on the network may be pretending to be the server."
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
            Text(text = "Fingerprint", style = TmTheme.typography.caption, color = colors.textMuted)
            // 32 pairs, split in half so each line can be read off against the server's
            certificate.fingerprint.split(":").chunked(16).forEach { line ->
                Text(text = line.joinToString(":"), style = TmTheme.typography.numeric, color = colors.text)
            }
        }
    }
}
