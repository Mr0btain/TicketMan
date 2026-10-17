package org.mr0btain.ticketman.components.print

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult
import java.lang.ref.WeakReference

private const val TAG = "PrintDocument"

/**
 * Android printing needs the showing activity, MainActivity hands itself over here when it starts
 */
object AndroidPrintHost {
    private var activity: WeakReference<Activity>? = null

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    val current: Activity?
        get() = activity?.get()
}

// Kept so the web view isn't collected before printing finishes
private var printingView: WebView? = null

/**
 * Loads the document as HTML into a web view and hands it to Android's print system, which shows its dialog
 */
actual suspend fun printDocument(document: PrintDocument, paper: PaperSize): String? {
    val activity = AndroidPrintHost.current ?: return "Printing isn't ready yet"

    return withContext(Dispatchers.Main) {
        try {
            val view = WebView(activity)
            printingView = view

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(page: WebView, url: String?) {
                    val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(
                            when (paper) {
                                // 80mm wide, long enough for a slip, the printer driver trims the rest
                                PaperSize.RECEIPT_80MM -> PrintAttributes.MediaSize("receipt_80mm", "80mm receipt", 3150, 11000)
                                PaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
                            }
                        )
                        .build()

                    manager.print(document.title, page.createPrintDocumentAdapter(document.title), attributes)
                }
            }

            view.loadDataWithBaseURL(null, HtmlPrintRenderer.render(document, paper), "text/html", "utf-8", null)

            AppLogger.m(TAG, SimpleResult.SUCCESS)
            null
        } catch (ex: Exception) {
            AppLogger.e(TAG, SimpleResult.FAIL, ex)
            "Couldn't print: ${ex.message}"
        }
    }
}
