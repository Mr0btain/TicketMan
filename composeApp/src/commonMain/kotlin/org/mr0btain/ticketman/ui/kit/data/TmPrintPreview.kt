package org.mr0btain.ticketman.ui.kit.data

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.mr0btain.ticketman.components.print.Code128
import org.mr0btain.ticketman.components.print.PrintBlock
import org.mr0btain.ticketman.components.print.PrintDocument
import org.mr0btain.ticketman.data.shop.PaperSize
import org.mr0btain.ticketman.ui.kit.inputs.TmSignatureView
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

// Paper stays white with black ink in every theme, like what comes out of the printer
private val PAPER = Color.White
private val INK = Color.Black
private val RULE = Color.Gray

/**
 * A [document] drawn on a sheet of [paper], to look it over before printing. Wider paper is kept to what fits
 */
@Composable
fun TmPrintPreview(document: PrintDocument, paper: PaperSize, modifier: Modifier = Modifier) {
    val spacing = TmTheme.spacing

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = paper.previewWidth())
                .fillMaxWidth()
                .clip(TmTheme.shapes.chip)
                .background(PAPER)
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            document.blocks.forEach { PreviewBlock(it) }
        }
    }
}

// About a printed page's width at the size it's read on screen
private fun PaperSize.previewWidth() = when (this) {
    PaperSize.RECEIPT_80MM -> 302.dp
    PaperSize.LETTER -> 612.dp
    PaperSize.LABEL -> 216.dp
}

@Composable
private fun PreviewBlock(block: PrintBlock) {
    val type = TmTheme.typography

    when (block) {
        is PrintBlock.Heading -> Text(
            text = block.text,
            modifier = Modifier.fillMaxWidth(),
            style = if (block.large) type.numericDisplay else type.title,
            color = INK,
            textAlign = TextAlign.Center
        )

        is PrintBlock.Text -> Text(
            text = block.text,
            modifier = Modifier.fillMaxWidth(),
            style = (if (block.small) type.caption else type.body).ink(block.bold),
            textAlign = if (block.centered) TextAlign.Center else TextAlign.Start
        )

        is PrintBlock.Field -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
            Text(text = block.label, modifier = Modifier.width(96.dp), style = type.caption.ink())
            Text(text = block.value, modifier = Modifier.weight(1f), style = type.body.ink())
        }

        is PrintBlock.Amounts -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TmTheme.spacing.sm)) {
                    Text(text = row.label, modifier = Modifier.weight(1f), style = type.body.ink(row.bold))
                    Text(text = row.amount, style = type.numeric.ink(row.bold))
                }
            }
        }

        PrintBlock.Divider -> Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(RULE) }

        PrintBlock.Gap -> Spacer(Modifier.height(TmTheme.spacing.sm))

        is PrintBlock.Qr -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TmQrCode(text = block.text, size = 112.dp) }

        is PrintBlock.Barcode -> Code128.encode(block.text)?.let { modules ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth(0.8f).height(40.dp)) {
                    val unit = size.width / modules.sum()
                    var x = 0f
                    modules.forEachIndexed { index, width ->
                        if (index % 2 == 0) drawRect(INK, topLeft = Offset(x, 0f), size = Size(width * unit, size.height))
                        x += width * unit
                    }
                }
                Text(text = block.text, style = type.caption.ink())
            }
        }

        is PrintBlock.Photo -> {
            val picture = remember(block) { block.jpeg.decodeToImageBitmap() }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(bitmap = picture, contentDescription = null, modifier = Modifier.width(112.dp).aspectRatio(block.aspectRatio))
            }
        }

        is PrintBlock.Signature -> Column(Modifier.fillMaxWidth(0.7f)) {
            TmSignatureView(modifier = Modifier.fillMaxWidth().aspectRatio(block.aspectRatio), strokes = block.strokes, ink = INK)
            Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawRect(RULE) }
            Text(text = block.caption, style = type.caption.ink())
        }
    }
}

private fun TextStyle.ink(bold: Boolean = false) = copy(color = INK, fontWeight = if (bold) FontWeight.Bold else fontWeight)
