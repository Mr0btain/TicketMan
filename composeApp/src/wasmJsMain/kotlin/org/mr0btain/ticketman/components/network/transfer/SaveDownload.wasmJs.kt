package org.mr0btain.ticketman.components.network.transfer

import io.ktor.client.*
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement

// The page can't stream to disk, so the browser downloads the link itself. That works because the link carries
// its own token rather than needing our login header
actual suspend fun saveDownload(
    client: HttpClient,
    url: String,
    fileName: String,
    expectedBytes: Long?,
    onProgress: (received: Long, total: Long?) -> Unit
): DownloadOutcome {
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = url
    link.download = fileName
    link.style.display = "none"

    document.body?.appendChild(link)
    link.click()
    link.remove()

    return DownloadOutcome.HandedToBrowser
}
