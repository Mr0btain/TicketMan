package org.mr0btain.ticketman.components.print

import org.mr0btain.ticketman.data.shop.PaperSize

/**
 * Turns a [PrintDocument] into a standalone HTML page sized for the paper, for printing through a browser or web view
 */
object HtmlPrintRenderer {
    fun render(document: PrintDocument, paper: PaperSize): String {
        val (pageSize, width, fontSize, largeSize) = when (paper) {
            PaperSize.RECEIPT_80MM -> PageStyle("80mm auto", "72mm", "11px", "26px")
            PaperSize.LETTER -> PageStyle("letter", "7in", "14px", "40px")
        }

        val body = document.blocks.joinToString("\n") { block(it, paper) }

        return """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <title>${escape(document.title)}</title>
            <style>
              @page { size: $pageSize; margin: ${if (paper == PaperSize.LETTER) "0.5in" else "4mm"}; }
              body { font-family: Helvetica, Arial, sans-serif; font-size: $fontSize; color: #000; margin: 0; }
              .page { width: $width; margin: 0 auto; }
              h1 { font-size: 1.4em; text-align: center; margin: 0.3em 0; }
              h1.large { font-size: $largeSize; letter-spacing: 0.05em; }
              p { margin: 0.2em 0; white-space: pre-wrap; }
              .small { font-size: 0.85em; }
              .center { text-align: center; }
              .bold { font-weight: bold; }
              .field { display: flex; gap: 0.6em; margin: 0.15em 0; }
              .field .label { font-weight: bold; min-width: 6.5em; }
              .row { display: flex; justify-content: space-between; margin: 0.1em 0; }
              hr { border: none; border-top: 1px dashed #000; margin: 0.6em 0; }
              .gap { height: 0.6em; }
              .qr, .signature { display: block; margin: 0.4em auto; }
              .signature { max-width: ${if (paper == PaperSize.LETTER) "4in" else "100%"}; }
              .caption { font-size: 0.85em; text-align: center; }
            </style>
            </head>
            <body><div class="page">
            $body
            </div></body>
            </html>
        """.trimIndent()
    }

    private data class PageStyle(val pageSize: String, val width: String, val fontSize: String, val largeSize: String)

    private fun block(block: PrintBlock, paper: PaperSize): String = when (block) {
        is PrintBlock.Heading -> "<h1${if (block.large) " class=\"large\"" else ""}>${escape(block.text)}</h1>"

        is PrintBlock.Text -> {
            val classes = listOfNotNull("bold".takeIf { block.bold }, "small".takeIf { block.small }, "center".takeIf { block.centered })
            "<p class=\"${classes.joinToString(" ")}\">${escape(block.text)}</p>"
        }

        is PrintBlock.Field -> "<div class=\"field\"><span class=\"label\">${escape(block.label)}</span><span>${escape(block.value)}</span></div>"

        is PrintBlock.Amounts -> block.rows.joinToString("") { row ->
            "<div class=\"row${if (row.bold) " bold" else ""}\"><span>${escape(row.label)}</span><span>${escape(row.amount)}</span></div>"
        }

        PrintBlock.Divider -> "<hr>"
        PrintBlock.Gap -> "<div class=\"gap\"></div>"
        is PrintBlock.Qr -> qrSvg(block.text, if (paper == PaperSize.LETTER) "1.4in" else "30mm")
        is PrintBlock.Signature -> signatureSvg(block) + "<div class=\"caption\">${escape(block.caption)}</div>"
    }

    /** QR as one SVG path, with the quiet zone the spec asks for */
    fun qrSvg(text: String, size: String): String {
        val code = QrCode.encode(text)
        val quiet = 4
        val total = code.size + quiet * 2

        val path = buildString {
            for (y in 0 until code.size) {
                for (x in 0 until code.size) {
                    if (code[x, y]) append("M${x + quiet},${y + quiet}h1v1h-1z")
                }
            }
        }

        return "<svg class=\"qr\" width=\"$size\" height=\"$size\" viewBox=\"0 0 $total $total\" shape-rendering=\"crispEdges\">" +
            "<rect width=\"$total\" height=\"$total\" fill=\"#fff\"/><path d=\"$path\" fill=\"#000\"/></svg>"
    }

    private fun signatureSvg(signature: PrintBlock.Signature): String {
        val width = 1000
        val height = (width / signature.aspectRatio.coerceIn(1f, 6f)).toInt()

        val paths = signature.strokes.joinToString("") { stroke ->
            val points = stroke.joinToString(" ") { "${(it.x * width).toInt()},${(it.y * height).toInt()}" }
            "<polyline points=\"$points\" fill=\"none\" stroke=\"#000\" stroke-width=\"6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
        }

        return "<svg class=\"signature\" width=\"100%\" viewBox=\"0 0 $width $height\">$paths" +
            "<line x1=\"0\" y1=\"${height - 2}\" x2=\"$width\" y2=\"${height - 2}\" stroke=\"#000\" stroke-width=\"2\"/></svg>"
    }

    fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
