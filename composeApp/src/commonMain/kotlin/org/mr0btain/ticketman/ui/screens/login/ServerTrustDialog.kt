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
 * Asks whether to trust a server whose certificate no public authority vouches for: a reverse proxy with a private
 * certificate, like Caddy's own CA on a shop's network. It warns that this isn't a public certificate and asks for
 * the fingerprint to be checked against the one the admin gave out. A changed certificate gets a stronger warning,
 * since it can mean something is pretending to be the server
 */
@Composable
fun ServerTrustDialog(
    certificate: UntrustedCertificate,
    onTrust: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = TmTheme.colors

    TmDialog(
        title = if (certificate.changed) "This server's certificate changed" else "Private certificate",
        description = "${certificate.address} has a private certificate, not one from a public authority like the " +
                "ones websites use, so this device can't tell on its own that it's the shop's server.",
        onDismissRequest = onDismiss,
        dismissOnOutsideClick = false,
        maxWidth = 520.dp,
        actions = {
            TmButtonRow(
                confirmText = if (certificate.changed) "Trust the new one" else "It matches, trust it",
                confirmVariant = if (certificate.changed) TmButtonVariant.DANGER else TmButtonVariant.PRIMARY,
                cancelText = "Don't connect",
                onConfirm = onTrust,
                onCancel = onDismiss
            )
        }
    ) {
        if (certificate.changed) {
            TmNotice(
                kind = TmNoticeKind.ERROR,
                title = "Check with your admin first",
                text = "Only trust it if the certificate was replaced on purpose, like after moving the server to a " +
                        "new machine or resetting the proxy. Otherwise something on the network may be pretending to " +
                        "be the server."
            )
        } else {
            TmNotice(
                kind = TmNoticeKind.WARNING,
                title = "Only if you know it's private",
                text = "Trust it only if your admin set the server up with a private certificate and the fingerprint " +
                        "below matches the one they gave you. A server with a public certificate never asks this. " +
                        "If you're not sure, don't connect."
            )
        }

        if (certificate.isAuthority) {
            Text(
                text = "This trusts the certificate authority that issues the server's certificates " +
                        "(${certificate.subject.removePrefix("CN=")}), for this address only.",
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(TmTheme.spacing.xxs)) {
            Text(
                text = if (certificate.isAuthority) "Certificate authority fingerprint" else "Fingerprint",
                style = TmTheme.typography.caption,
                color = colors.textMuted
            )
            // 32 pairs, split in half so each line can be read off against the admin's
            certificate.fingerprint.split(":").chunked(16).forEach { line ->
                Text(text = line.joinToString(":"), style = TmTheme.typography.numeric, color = colors.text)
            }
        }
    }
}
