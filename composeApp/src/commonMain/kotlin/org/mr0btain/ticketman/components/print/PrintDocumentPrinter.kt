package org.mr0btain.ticketman.components.print

import org.mr0btain.ticketman.data.shop.PaperSize

/**
 * Sends a document to a printer, showing the platform's print dialog so they can pick the printer.
 * Desktop draws it straight onto the page, web and Android print it as HTML
 * @return Null when it was sent or they cancelled, otherwise what went wrong
 */
expect suspend fun printDocument(document: PrintDocument, paper: PaperSize): String?
