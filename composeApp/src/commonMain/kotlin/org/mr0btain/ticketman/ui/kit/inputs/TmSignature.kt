package org.mr0btain.ticketman.ui.kit.inputs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.mr0btain.ticketman.data.ticket.SignaturePoint
import org.mr0btain.ticketman.ui.kit.theme.TmTheme

/**
 * Where the customer signs with a finger, stylus or mouse. Strokes come out scaled to 0..1 so they don't depend on
 * the pad's size
 * @param strokes Strokes drawn so far, owned by the caller so it can clear them
 * @param onStrokesChange Gets the strokes after each change
 * @param onSize Gets the pad's width over height, saved with the signature
 */
@Composable
fun TmSignaturePad(
    modifier: Modifier = Modifier,
    strokes: List<List<SignaturePoint>>,
    onStrokesChange: (List<List<SignaturePoint>>) -> Unit,
    onSize: (Float) -> Unit
) {
    // The stroke being drawn, in pad coordinates
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(Size.Zero) }
    val latestStrokes by rememberUpdatedState(strokes)
    val ink = TmTheme.colors.text
    val shape = TmTheme.shapes.control

    Box(
        modifier = modifier
            .clip(shape)
            .background(TmTheme.colors.surfaceSunken)
            .border(1.5.dp, TmTheme.colors.accent.color, shape)
            .onSizeChanged { measured ->
                size = measured.toSize()
                if (measured.height > 0) onSize(measured.width.toFloat() / measured.height)
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start -> current = listOf(start) },
                        onDrag = { change, _ ->
                            current = current + change.position
                            change.consume()
                        },
                        onDragEnd = {
                            if (current.size > 1 && size.width > 0 && size.height > 0) {
                                val scaled = current.map { point ->
                                    SignaturePoint(
                                        (point.x / size.width).coerceIn(0f, 1f),
                                        (point.y / size.height).coerceIn(0f, 1f)
                                    )
                                }
                                onStrokesChange(latestStrokes + listOf(scaled))
                            }
                            current = emptyList()
                        },
                        onDragCancel = { current = emptyList() }
                    )
                }
        ) {
            // Signing line
            drawLine(
                color = ink.copy(alpha = 0.3f),
                start = Offset(this.size.width * 0.05f, this.size.height * 0.8f),
                end = Offset(this.size.width * 0.95f, this.size.height * 0.8f),
                strokeWidth = 2f
            )

            strokes.forEach { stroke -> drawStroke(stroke.map { Offset(it.x * this.size.width, it.y * this.size.height) }, ink) }
            drawStroke(current, ink)
        }

        if (strokes.isEmpty() && current.isEmpty()) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = "Sign here",
                style = TmTheme.typography.title,
                color = TmTheme.colors.textFaint
            )
        }
    }
}

/**
 * Draws a saved signature, fitting it into the space given
 */
@Composable
fun TmSignatureView(
    modifier: Modifier = Modifier,
    strokes: List<List<SignaturePoint>>,
    ink: Color = TmTheme.colors.text
) {
    Canvas(modifier = modifier) {
        strokes.forEach { stroke -> drawStroke(stroke.map { Offset(it.x * size.width, it.y * size.height) }, ink) }
    }
}

private fun DrawScope.drawStroke(points: List<Offset>, color: Color) {
    if (points.size < 2) return

    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }

    drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
