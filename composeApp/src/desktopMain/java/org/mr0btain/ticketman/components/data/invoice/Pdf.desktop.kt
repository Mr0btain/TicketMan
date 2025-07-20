package org.mr0btain.ticketman.components.data.invoice

import com.lowagie.text.Cell
import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Table
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfWriter
import org.mr0btain.ticketman.data.TicketInfo
import org.mr0btain.ticketman.components.utils.formatIntToMoney
import org.mr0btain.ticketman.data.CustomerData
import java.awt.Color
import java.io.FileOutputStream

actual fun makeTicketInvoicePdf(
    filePath: String,
    fileName: String,
    customerData: CustomerData,
    ticketInfo: TicketInfo
) {
    // Pdf file to output to
    val pdfOutputFile = FileOutputStream("$filePath/$fileName")

    // Fonts for text
    val titleFontSize = 25f
    val baseFont: BaseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false)
    val regularFont = Font(baseFont, 10f, Font.NORMAL, Color.BLACK)
    val columnHeadersFont = Font(baseFont, 10f, Font.NORMAL, Color.BLACK)
    val columnItemsFont = Font(baseFont, 8f, Font.NORMAL, Color.BLACK)

    // Margins
    val xMarginStart = 45f
    val xPositionForRightText = 375f

    // Customer information section
    val customerInfoParagraph = listOf(
        "${customerData.name.firstName} ${customerData.name.lastName}",
        customerData.address.street,
        "${customerData.address.city}, ${customerData.address.state}",
        customerData.address.zip
    )

    // Invoice / ticket information section
    val invoiceParagraph = listOf(
        "Invoice #: ${ticketInfo.invoice.invoiceNumber}",
        "Ticket #: ${ticketInfo.identifier}",
        "Employee #: ${ticketInfo.assignedUser}"
    )

    // PDF stuff
    val myPDFDoc = Document(PageSize.A4, 0f, 0f, 2f, 0f)
    val pdfWriter = PdfWriter.getInstance(myPDFDoc, pdfOutputFile)

    // Table for charges
    val myTable = Table(5).apply {
        padding = 2f
        spacing = 1f
        setWidths(floatArrayOf(200f, 500f,100f, 100f, 100f))
    }

    // Create table header
    listOf("Name", "Description", "Count", "Price", "Cost").forEach {
        val current = Cell(Phrase(it, columnHeadersFont)).apply{
            isHeader = true
            setBackgroundColor(Color.LIGHT_GRAY)
        }

        myTable.addCell(current)
    }

    // Add charges to table
    ticketInfo.invoice.charges.forEach{ charge ->
        myTable.apply {
            addCell(Cell(Phrase(charge.name, columnItemsFont)))
            addCell(Cell(Phrase(charge.description, columnItemsFont)))
            addCell(Cell(Phrase(charge.amount.toString(), columnItemsFont)))
            addCell(Cell(Phrase("$ ${formatIntToMoney(charge.cost)}", columnItemsFont)))
            addCell(Cell(Phrase("$ ${formatIntToMoney(charge.cost * charge.amount)}", columnItemsFont)))
        }
    }

    // Get the total of the charges
    var total = 0
    ticketInfo.invoice.charges.forEach { total += it.cost * it.amount }

    // Last table add total
    myTable.apply {
        addCell(Cell(Phrase("Total", columnItemsFont)))
        addCell(Cell(Phrase("", columnItemsFont)))
        addCell(Cell(Phrase("", columnItemsFont)))
        addCell(Cell(Phrase("", columnItemsFont)))
        addCell(Cell(Phrase("$ ${formatIntToMoney(total)}", columnItemsFont)))
    }

    // Open the document and start adding content
    myPDFDoc.apply {
        // Metadata section
        addTitle("Customer Invoice")
        addSubject("Invoice for work completed")
        //addKeywords("")
        addCreator("TicketMan")
        //addAuthor("")

        // Open document for editing
        open()

        // For writing direct content
        val canvas = pdfWriter.directContent

        // Begin drawing text
        canvas.beginText()

        // Title
        canvas.setFontAndSize(regularFont.baseFont, titleFontSize)
        canvas.setTextMatrix(xMarginStart, 750f)
        canvas.showText("Invoice")

        // Customer section
        canvas.setFontAndSize(regularFont.baseFont, regularFont.size)
        canvas.setTextMatrix(xMarginStart, 700f)
        canvas.showText(customerInfoParagraph[0])
        canvas.setTextMatrix(xMarginStart, 685f)
        canvas.showText(customerInfoParagraph[1])
        canvas.setTextMatrix(xMarginStart, 670f)
        canvas.showText(customerInfoParagraph[2])
        canvas.setTextMatrix(xMarginStart, 655f)
        canvas.showText(customerInfoParagraph[3])

        // Invoice details
        canvas.setTextMatrix(xPositionForRightText, 700f)
        canvas.showText(invoiceParagraph[0])
        canvas.setTextMatrix(xPositionForRightText, 685f)
        canvas.showText(invoiceParagraph[1])
        canvas.setTextMatrix(xPositionForRightText, 670f)
        canvas.showText(invoiceParagraph[2])

        // End text
        canvas.endText()

        // Add some spacing
        add(Paragraph("\n\n\n\n\n\n\n\n\n\n\n"))

        // Table for charges
        add(myTable)

        // Close document
        close()
    }

    // Close file writer
    pdfWriter.close()
}