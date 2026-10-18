package org.mr0btain.ticketman.components.print

import kotlinx.browser.window
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import org.w3c.dom.HTMLIFrameElement

private const val TAG = "PrintDocument"

/**
 * Prints through a hidden frame holding the document as HTML, which brings up the browser's print dialog
 */
actual suspend fun printDocument(document: PrintDocument, paper: PaperSize): String? {
    return try {
        // The parameter is called document too, so the page's one is spelled out
        val frame = kotlinx.browser.document.createElement("iframe") as HTMLIFrameElement
        frame.style.position = "fixed"
        frame.style.width = "0"
        frame.style.height = "0"
        frame.style.border = "0"

        frame.onload = {
            frame.contentWindow?.focus()
            frame.contentWindow?.print()

            // Give the dialog time to take a copy before the frame goes
            window.setTimeout({
                frame.remove()
                null
            }, 60_000)
            null
        }

        frame.srcdoc = HtmlPrintRenderer.render(document, paper)
        kotlinx.browser.document.body?.appendChild(frame)

        AppLogger.m(TAG, SimpleResult.SUCCESS)
        null
    } catch (ex: Throwable) {
        AppLogger.e(TAG, SimpleResult.FAIL, ex)
        "Couldn't print: ${ex.message}"
    }
}
