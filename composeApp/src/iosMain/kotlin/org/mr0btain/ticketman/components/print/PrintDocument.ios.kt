package org.mr0btain.ticketman.components.print

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import platform.UIKit.UIApplication
import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIDevice

private const val TAG = "PrintDocument"

/**
 * Hands the document as HTML to the system print sheet (AirPrint), the same HTML web and Android print. The sheet
 * picks the paper from the printer, so [paper] only shapes the page through the HTML's own styles
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun printDocument(document: PrintDocument, paper: PaperSize): String? {
    if (!UIPrintInteractionController.isPrintingAvailable()) return "This device can't print"

    return withContext(Dispatchers.Main) {
        try {
            val controller = UIPrintInteractionController.sharedPrintController()
            controller.printInfo = UIPrintInfo.printInfo().apply {
                setJobName(document.title)
                setOutputType(UIPrintInfoOutputType.UIPrintInfoOutputGeneral)
            }
            controller.printFormatter = UIMarkupTextPrintFormatter(markupText = HtmlPrintRenderer.render(document, paper))

            // An iPad shows the sheet as a popover, which needs somewhere to point; the middle of the screen will do
            if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
                val view = UIApplication.sharedApplication.keyWindow?.rootViewController?.view
                    ?: return@withContext "Printing isn't ready yet"
                val rect = view.bounds.useContents {
                    platform.CoreGraphics.CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0)
                }
                controller.presentFromRect(rect, inView = view, animated = true, completionHandler = null)
            } else {
                controller.presentAnimated(true, completionHandler = null)
            }

            AppLogger.m(TAG, SimpleResult.SUCCESS)
            null
        } catch (ex: Exception) {
            AppLogger.e(TAG, SimpleResult.FAIL, ex)
            "Couldn't print: ${ex.message}"
        }
    }
}
