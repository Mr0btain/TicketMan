package org.mr0btain.ticketman.components.print

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import java.awt.print.PrinterJob

private const val TAG = "PrintDocument"

actual suspend fun printDocument(document: PrintDocument, paper: PaperSize): String? {
    return try {
        val layout = Java2DPrintLayout(document, paper)
        val job = PrinterJob.getPrinterJob()
        job.jobName = document.title
        job.setPrintable(layout, layout.pageFormat(job))

        // The dialog is Swing, printing itself can take a while so it goes off the UI thread
        val confirmed = withContext(Dispatchers.Swing) { job.printDialog() }
        if (confirmed) withContext(Dispatchers.IO) { job.print() }

        AppLogger.m(TAG, SimpleResult.SUCCESS)
        null
    } catch (ex: Exception) {
        AppLogger.e(TAG, SimpleResult.FAIL, ex)
        "Couldn't print: ${ex.message ?: ex::class.simpleName}"
    }
}
