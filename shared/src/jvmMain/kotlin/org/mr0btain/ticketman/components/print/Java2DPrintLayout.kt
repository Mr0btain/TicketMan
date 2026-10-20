package org.mr0btain.ticketman.components.print

import org.mr0btain.ticketman.data.shop.PaperSize
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.awt.print.PrinterJob

/**
 * Lays a [PrintDocument] out with Java2D. Receipts are one page as long as the content, letter splits across pages.
 * The desktop client prints through it as a [Printable]; the server draws the same pages into a PDF with [drawPage]
 */
class Java2DPrintLayout(
    document: PrintDocument,
    private val paper: PaperSize
) : Printable {
    private val receipt = paper == PaperSize.RECEIPT_80MM

    // Points, 72 to the inch
    private val paperWidth = if (receipt) mm(80) else 612.0
    private val letterHeight = 792.0
    private val margin = if (receipt) mm(4) else 36.0
    val contentWidth = paperWidth - margin * 2

    private val fontName = Font.SANS_SERIF
    private val baseSize = if (receipt) 8f else 11f
    private val regular = Font(fontName, Font.PLAIN, 1).deriveFont(baseSize)
    private val bold = regular.deriveFont(Font.BOLD)
    private val small = regular.deriveFont(baseSize * 0.85f)
    private val heading = bold.deriveFont(baseSize * 1.4f)
    private val large = bold.deriveFont(baseSize * if (receipt) 2.6f else 3.2f)

    private val labelWidth = if (receipt) 58.0 else 110.0
    private val qrSize = if (receipt) mm(30) else 100.0

    // Measuring needs a Graphics, a throwaway image's is fine
    private val measure: Graphics2D = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics()

    /** Something to draw and how tall it is */
    private class Item(val height: Double, val draw: (Graphics2D, Double) -> Unit)

    private val items: List<Item> = document.blocks.flatMap { layout(it) }

    /** Items split into pages */
    private val pages: List<List<Item>> = paginate()

    val pageCount: Int get() = pages.size

    /** Height of everything, used for the receipt's page length */
    val contentHeight: Double = items.sumOf { it.height }

    fun pageFormat(job: PrinterJob): PageFormat {
        val format = job.defaultPage()
        val sheet = Paper()
        val height = pageHeight

        sheet.setSize(paperWidth, height)
        sheet.setImageableArea(margin, margin, contentWidth, height - margin * 2)
        format.paper = sheet
        format.orientation = PageFormat.PORTRAIT

        return format
    }

    /** Page width in points */
    val pageWidth: Double get() = paperWidth

    /** Page height in points: a letter page, or a receipt as long as what's on it */
    val pageHeight: Double get() = if (receipt) contentHeight + margin * 2 else letterHeight

    /** The space around the content, in points */
    val pageMargin: Double get() = margin

    override fun print(graphics: Graphics, pageFormat: PageFormat, pageIndex: Int): Int {
        if (pageIndex >= pages.size) return Printable.NO_SUCH_PAGE

        val g = graphics as Graphics2D
        g.translate(pageFormat.imageableX, pageFormat.imageableY)
        drawPage(g, pageIndex)

        return Printable.PAGE_EXISTS
    }

    /** Draws page [pageIndex] with its top left content corner at the graphics' origin, margins already left out */
    fun drawPage(g: Graphics2D, pageIndex: Int) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.BLACK

        var y = 0.0
        pages[pageIndex].forEach { item ->
            item.draw(g, y)
            y += item.height
        }
    }

    private fun paginate(): List<List<Item>> {
        if (receipt) return listOf(items)

        val usable = letterHeight - margin * 2
        val result = mutableListOf(mutableListOf<Item>())
        var used = 0.0

        items.forEach { item ->
            if (used + item.height > usable && result.last().isNotEmpty()) {
                result.add(mutableListOf())
                used = 0.0
            }
            result.last().add(item)
            used += item.height
        }

        return result
    }

    // ------------------------------------------ Laying out blocks ------------------------------------------

    private fun layout(block: PrintBlock): List<Item> = when (block) {
        is PrintBlock.Heading -> textLines(block.text, if (block.large) large else heading, centered = true, spaceAfter = 2.0)
        is PrintBlock.Text -> textLines(block.text, if (block.small) small else if (block.bold) bold else regular, centered = block.centered)
        is PrintBlock.Field -> listOf(field(block.label, block.value))
        is PrintBlock.Amounts -> block.rows.map { amount(it) }
        PrintBlock.Divider -> listOf(divider())
        PrintBlock.Gap -> listOf(Item(baseSize * 0.6) { _, _ -> })
        is PrintBlock.Qr -> listOf(qr(block.text))
        is PrintBlock.Signature -> listOf(signature(block)) + textLines(block.caption, small, centered = true)
    }

    private fun metrics(font: Font): FontMetrics = measure.getFontMetrics(font)

    // Breaks text into lines that fit, keeping any line breaks it already had
    private fun wrap(text: String, font: Font, width: Double): List<String> {
        val metrics = metrics(font)

        return text.split("\n").flatMap { paragraph ->
            val lines = mutableListOf<String>()
            var line = ""

            paragraph.split(" ").forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"

                if (metrics.stringWidth(candidate) <= width || line.isEmpty()) {
                    line = candidate
                } else {
                    lines.add(line)
                    line = word
                }
            }

            lines.add(line)
            lines
        }
    }

    private fun lineHeight(font: Font) = metrics(font).height.toDouble()

    private fun textLines(text: String, font: Font, centered: Boolean, spaceAfter: Double = 0.0): List<Item> {
        val lines = wrap(text, font, contentWidth)

        return lines.mapIndexed { index, line ->
            val extra = if (index == lines.lastIndex) spaceAfter else 0.0

            Item(lineHeight(font) + extra) { g, y ->
                g.font = font
                val width = g.fontMetrics.stringWidth(line)
                val x = if (centered) (contentWidth - width) / 2 else 0.0
                g.drawString(line, x.toFloat(), (y + g.fontMetrics.ascent).toFloat())
            }
        }
    }

    private fun field(label: String, value: String): Item {
        val valueLines = wrap(value, regular, contentWidth - labelWidth)
        val height = lineHeight(regular) * valueLines.size

        return Item(height) { g, y ->
            g.font = bold
            g.drawString(label, 0f, (y + g.fontMetrics.ascent).toFloat())

            g.font = regular
            valueLines.forEachIndexed { index, line ->
                g.drawString(line, labelWidth.toFloat(), (y + index * lineHeight(regular) + g.fontMetrics.ascent).toFloat())
            }
        }
    }

    private fun amount(row: AmountRow): Item {
        val font = if (row.bold) bold else regular
        val amountWidth = metrics(font).stringWidth(row.amount) + 6.0
        val labelLines = wrap(row.label, font, contentWidth - amountWidth)
        val height = lineHeight(font) * labelLines.size

        return Item(height) { g, y ->
            g.font = font
            val ascent = g.fontMetrics.ascent

            labelLines.forEachIndexed { index, line ->
                g.drawString(line, 0f, (y + index * lineHeight(font) + ascent).toFloat())
            }

            // Amount lines up with the first line, right aligned
            g.drawString(row.amount, (contentWidth - g.fontMetrics.stringWidth(row.amount)).toFloat(), (y + ascent).toFloat())
        }
    }

    private fun divider(): Item = Item(baseSize.toDouble()) { g, y ->
        val oldStroke = g.stroke
        g.stroke = BasicStroke(0.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(2f, 2f), 0f)
        val lineY = y + baseSize / 2.0
        g.draw(Line2D.Double(0.0, lineY, contentWidth, lineY))
        g.stroke = oldStroke
    }

    private fun qr(text: String): Item {
        val code = QrCode.encode(text)
        val module = qrSize / (code.size + 8)

        return Item(qrSize + 4) { g, y ->
            val left = (contentWidth - qrSize) / 2 + module * 4
            val top = y + module * 4

            // Smoothing leaves hairline gaps between modules that scanners trip over, so draw it sharp
            val smoothing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

            // Each run of dark modules in a row is one rectangle, so there are no seams inside a run either
            for (row in 0 until code.size) {
                var column = 0

                while (column < code.size) {
                    if (!code[column, row]) {
                        column++
                        continue
                    }

                    val start = column
                    while (column < code.size && code[column, row]) column++

                    g.fill(Rectangle2D.Double(left + start * module, top + row * module, (column - start) * module, module))
                }
            }

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, smoothing)
        }
    }

    private fun signature(block: PrintBlock.Signature): Item {
        // Full width on a receipt, about four inches on a letter page so it looks like a signature
        val width = if (receipt) contentWidth else minOf(contentWidth, 288.0)
        val height = width / block.aspectRatio.coerceIn(1f, 6f)
        val left = (contentWidth - width) / 2

        return Item(height + 4) { g, y ->
            val oldStroke = g.stroke
            g.stroke = BasicStroke(if (receipt) 1.2f else 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

            block.strokes.filter { it.size > 1 }.forEach { stroke ->
                val path = Path2D.Double()
                path.moveTo(left + stroke.first().x * width, y + stroke.first().y * height)
                stroke.drop(1).forEach { path.lineTo(left + it.x * width, y + it.y * height) }
                g.draw(path)
            }

            g.stroke = BasicStroke(0.6f)
            g.draw(Line2D.Double(left, y + height, left + width, y + height))
            g.stroke = oldStroke
        }
    }

    private fun mm(millimeters: Int): Double = millimeters / 25.4 * 72
}
