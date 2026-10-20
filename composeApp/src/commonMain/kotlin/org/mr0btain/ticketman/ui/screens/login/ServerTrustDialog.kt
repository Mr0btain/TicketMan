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
 * Asks whether to trust a server whose certificate no authority vouches for, after checking its fingerprint against
 * the one the admin gave out. That's a server doing its own HTTPS, or a proxy with a self-signed certificate, whose
 * fingerprint isn't on the admin screen. A changed certificate gets a warning, since it can mean something is
 * pretending to be the server
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
        description = "${certificate.address} has a certificate no authority vouches for, so this device can't tell " +
                "it's the shop's server on its own. Check the code below matches the fingerprint your administrator " +
                "gave you. When the server does its own HTTPS, it's also on the admin Server tab and in the server's log.",
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
                text = "Only trust it if the certificate was replaced on purpose, like after moving the server to a " +
                        "new machine or renewing the proxy's certificate. Otherwise something on the network may be " +
                        "pretending to be the server."
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
