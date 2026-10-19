package org.mr0btain.ticketman.ui.screens.admin.components

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mr0btain.ticketman.components.network.RemoteHandler
import org.mr0btain.ticketman.components.network.transfer.DownloadOutcome
import org.mr0btain.ticketman.components.network.transfer.fileBody
import org.mr0btain.ticketman.components.network.transfer.fileBodyLimitBytes
import org.mr0btain.ticketman.components.network.transfer.isTooBigToSend
import org.mr0btain.ticketman.components.network.transfer.saveDownload
import org.mr0btain.ticketman.components.utils.formatBytes
import org.mr0btain.ticketman.data.shop.ShopProfile
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.request.SeedRequest
import org.mr0btain.ticketman.server.response.PendingRestore
import org.mr0btain.ticketman.server.response.ServerInfoResponse

/**
 * The Server tab: the shop profile, backups and restores, the HTTPS certificate, and mock data
 * @param onShopChanged Called after the shop profile is saved, so its currency shows everywhere
 * @param onSeeded Called after mock data is added, so lists and counts reload
 */
@Suppress("PrivatePropertyName")
class AdminServer(
    private val remoteHandler: RemoteHandler,
    private val support: AdminSupport,
    private val onShopChanged: (ShopProfile) -> Unit,
    private val onSeeded: suspend () -> Unit
) {
    private val TAG = "AdminServer"
    private val BACKUP_TAG = "$TAG:Backup"

    private val admin get() = remoteHandler.adminRepo
    private val scope get() = support.scope

    private val _serverInfo = MutableStateFlow<ServerInfoResponse?>(null)
    val serverInfo = _serverInfo.asStateFlow()

    // A restore the server will apply when it next starts
    private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore = _pendingRestore.asStateFlow()

    // Where a backup or restore upload is up to, null when none is running
    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress = _backupProgress.asStateFlow()

    private val _shopProfile = MutableStateFlow<ShopProfile?>(null)
    /** Also read by the overview, for the end of day report's paper size */
    val shopProfile = _shopProfile.asStateFlow()

    suspend fun reload() {
        _serverInfo.value = admin.getServerInfo()
        _pendingRestore.value = admin.getPendingRestore()
        reloadShopProfile()
    }

    suspend fun reloadShopProfile() {
        _shopProfile.value = admin.getShopProfile()
    }

    /** Saves the shop profile, used on invoices, slips and at check-in */
    fun saveShopProfile(profile: ShopProfile) {
        scope.launch {
            support.report(admin.updateShopProfile(profile), "Shop profile saved") {
                _shopProfile.value = profile
                onShopChanged(profile)
            }
        }
    }

    // ------------------------------------------ Backup and restore ------------------------------------------

    /**
     * Has the server build a backup, then streams it to a file picked here (or hands it to the browser)
     * @param includeImages Photos too, off for a quick database only copy
     */
    fun downloadBackup(includeImages: Boolean) {
        scope.launch {
            support.loading {
                _backupProgress.value = BackupProgress("Building the backup on the server")

                when (val prepared = admin.prepareBackup(includeImages)) {
                    is TmResult.Error -> support.notify("Backup failed: ${prepared.error.message}")

                    is TmResult.Success -> {
                        val ticket = prepared.data
                        _backupProgress.value = BackupProgress("Downloading ${formatBytes(ticket.sizeBytes)}", 0f)

                        val outcome = saveDownload(
                            client = remoteHandler.httpClient,
                            url = admin.backupDownloadUrl(ticket),
                            fileName = ticket.fileName,
                            expectedBytes = ticket.sizeBytes
                        ) { received, total ->
                            val size = total ?: ticket.sizeBytes
                            _backupProgress.value = BackupProgress(
                                text = "Downloading ${formatBytes(received)} of ${formatBytes(size)}",
                                fraction = if (size > 0) received.toFloat() / size else null
                            )
                        }

                        when (outcome) {
                            is DownloadOutcome.Saved -> support.notify("Backup saved (${formatBytes(outcome.bytes)})")
                            DownloadOutcome.HandedToBrowser -> support.notify("Backup downloading in the browser")
                            DownloadOutcome.Cancelled -> {
                                AppLogger.i(BACKUP_TAG, "Not saved")
                                support.notify("Backup wasn't saved")
                            }
                            is DownloadOutcome.Failed -> support.notify("Backup download failed: ${outcome.error.message}")
                        }
                    }
                }

                _backupProgress.value = null
            }
        }
    }

    /**
     * Sends a backup archive to the server to restore from. The server checks it and swaps it in the next time
     * it starts, keeping the current data aside
     */
    fun restoreFrom(file: PlatformFile) {
        if (isTooBigToSend(file)) {
            support.notify(tooBigToRestoreMessage(file))
            return
        }

        scope.launch {
            support.loading {
                _backupProgress.value = BackupProgress("Uploading ${file.name}", 0f)

                val result = admin.uploadRestore(fileBody(file, ContentType.Application.Zip)) { sent, total ->
                    _backupProgress.value = BackupProgress(
                        text = total?.let { "Uploading ${formatBytes(sent)} of ${formatBytes(it)}" } ?: "Uploading ${formatBytes(sent)}",
                        fraction = total?.takeIf { it > 0 }?.let { sent.toFloat() / it }
                    )
                }

                when (result) {
                    is TmResult.Success -> {
                        _pendingRestore.value = result.data
                        support.notify("Backup ready. Restart the server to restore it")
                    }

                    is TmResult.Error -> support.notify("Restore failed: ${result.error.message}")
                }

                _backupProgress.value = null
            }
        }
    }

    /** Drops the restore waiting for a restart */
    fun cancelRestore() {
        scope.launch {
            when (val result = admin.cancelRestore()) {
                is TmResult.Success -> {
                    _pendingRestore.value = null
                    support.notify("Restore cancelled")
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    // ------------------------------------------ Certificate ------------------------------------------

    /** Makes a new HTTPS certificate on the server, which it switches to when it next starts */
    fun renewCertificate() {
        scope.launch {
            support.loading {
                when (val result = admin.renewCertificate()) {
                    is TmResult.Success -> {
                        _serverInfo.value = _serverInfo.value?.copy(certificate = result.data)
                        support.notify("New certificate made. Restart the server to use it")
                    }

                    is TmResult.Error -> support.notify("Couldn't make a new certificate: ${result.error.message}")
                }
            }
        }
    }

    /** Drops the new certificate waiting for a restart */
    fun cancelCertificateRenewal() {
        scope.launch {
            when (val result = admin.cancelCertificateRenewal()) {
                is TmResult.Success -> {
                    _serverInfo.value = _serverInfo.value?.let { info ->
                        info.copy(certificate = info.certificate?.copy(pendingFingerprint = null, pendingExpiresAt = null))
                    }
                    support.notify("New certificate dropped")
                }

                is TmResult.Error -> support.notify(result.error.message)
            }
        }
    }

    /** Fills a development server with mock data */
    fun seed(request: SeedRequest, onSuccess: () -> Unit) {
        scope.launch {
            support.loading {
                support.report(admin.seed(request), "Added mock data") {
                    onSuccess()
                    onSeeded()
                }
            }
        }
    }

    fun clear() {
        _serverInfo.value = null
        _pendingRestore.value = null
        _shopProfile.value = null
    }
}

/** Said when a backup is too big for this client to upload, which only happens in the browser */
fun tooBigToRestoreMessage(file: PlatformFile): String =
    "${file.name} is ${formatBytes(file.size())}, more than the ${formatBytes(fileBodyLimitBytes ?: 0)} a browser can " +
        "upload. Restore it from the desktop app instead"

/**
 * A running backup, for the backup card
 * @param fraction 0 to 1 once the download size is known, null while the server builds it
 */
data class BackupProgress(
    val text: String,
    val fraction: Float? = null
)
