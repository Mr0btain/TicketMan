# Add a printout

A new slip, label or report. Printouts are described once, as a list of blocks, and each platform lays the blocks
out for the paper: desktop draws them with Java2D, web and Android print them as HTML. So a new printout is usually
one function and a menu entry. The example is an **estimate** slip a customer can take away before agreeing to a
repair. [Printing](../client/printing.md) explains the pieces.

## 1. Build the document

Add a function to `PrintTemplates` (`composeApp/.../components/print/PrintTemplates.kt`) that returns a
`PrintDocument`: a title (used for the print job's name) and its blocks, top to bottom.

```kotlin
/**
 * What the repair should cost, for the customer to take away before saying yes
 */
fun estimate(shop: ShopProfile, ticket: Ticket, customer: Customer?): PrintDocument =
    PrintDocument(
        "Estimate ${ticket.label}",
        shopHeader(shop) + listOfNotNull(
            PrintBlock.Divider,
            PrintBlock.Heading("Estimate"),
            PrintBlock.Heading(ticket.label, large = true),
            PrintBlock.Qr(ticket.label),
            PrintBlock.Divider
        ) + customerFields(customer) + deviceFields(ticket) + listOfNotNull(
            PrintBlock.Text(ticket.problem),
            ticket.estimateCents?.let { PrintBlock.Amounts(listOf(AmountRow("Estimate", Money.format(it), bold = true))) },
            PrintBlock.Gap,
            PrintBlock.Text("Prices may change once the device has been opened up.", small = true, centered = true)
        )
    )
```

The blocks there are:

| Block | For |
|-------|-----|
| `Heading(text, large)` | Centered titles. `large` is for a ticket number read across the counter |
| `Text(text, bold, small, centered)` | Paragraphs and notes |
| `Field(label, value)` | A label with its value beside it |
| `Amounts(rows)` | Text on the left, an amount on the right, like invoice lines and totals |
| `Divider`, `Gap` | A line, or space |
| `Qr(text)` | A QR code, encoded by the in-house `QrCode` |
| `Signature(strokes, aspectRatio, caption)` | A signature from check-in |

Reuse the private helpers (`shopHeader`, `customerFields`, `deviceFields`, `signatureBlocks`) so printouts share
their headers. Format money with `Money.format` and times with the helpers in `Formatting.kt`. Keep it to what
fits an 80 mm receipt; letter paper just gets wider margins.

## 2. Offer it

Counter printouts are picked from `PrintButton`'s menu, listed by `PrintTemplate`:

1. Add an entry to the `PrintTemplate` enum: `ESTIMATE("Estimate")`. The label is what the menu shows.
2. Add a branch to the `when` in `CounterPrinter.print(...)`. It's exhaustive, so the build fails until you do:

   ```kotlin
   PrintTemplate.ESTIMATE -> PrintTemplates.estimate(shop, detail.ticket, detail.customer)
   ```

   `print` loads the ticket fresh first, so the printout matches the server.
3. Add `PrintTemplate.ESTIMATE` to the `templates` list of the `PrintButton` where it should be offered, like the
   ticket card's in `CounterScreen`.

A printout that isn't about a ticket is printed from its own handler the same way `AdminHandler.printDailyReport`
does: build the document and pass it to `printDocument(document, paper)`, which shows the platform's print dialog
and returns null when it was sent or cancelled, or what went wrong.

## 3. A new kind of block

Only if none of the blocks above will do. Add it to the `PrintBlock` sealed interface, then draw it in both
renderers:

- `Java2DPrintLayout.layout(block)` in `desktopMain/.../print/PrintDocument.desktop.kt`, for desktop,
- `HtmlPrintRenderer.block(block, paper)` in `commonMain/.../print/HtmlPrintRenderer.kt`, for web and Android.

Both are exhaustive `when`s over the sealed interface, so the build tells you if one is missing. Check the HTML
escapes any text it puts in the page (`escape(...)`).

## 4. Test it

- **The blocks**, in `PrintTemplatesTest` (`commonTest`): that the estimate has its number, QR, customer and
  amount, the way `claimSlipHasTheNumberQrAndSignature` checks the claim slip.
- **The layout**, in `Java2DPrintLayoutTest` (`desktopTest`): lay it out on `PaperSize.RECEIPT_80MM` and
  `PaperSize.LETTER`, check the page count, and read the QR back with `scanQr`. These tests also write PNGs of the
  pages to the temp folder (`ticketman-claim-slip-letter.png` and the like), which is the quickest way to see it.
- **On paper.** Print it once on the shop's real receipt printer and a letter printer; drivers differ in margins.

## Checklist

- [ ] A `PrintTemplates` function returning a `PrintDocument`
- [ ] A `PrintTemplate` entry and a branch in `CounterPrinter.print`, or its own handler call
- [ ] Offered in a `PrintButton`
- [ ] Any new block drawn in `Java2DPrintLayout` and `HtmlPrintRenderer`
- [ ] `PrintTemplatesTest` and `Java2DPrintLayoutTest`
- [ ] Printed once on real printers

## See also

- [Printing](../client/printing.md)
- [Counter screen](../ui/screens/counter.md#printing)
- [Add platform-specific code](add-a-platform-actual.md)
