package org.mr0btain.ticketman.components.data.image

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.compressImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

class ImageUtils(val image: ImageBitmap) {
    fun resize(newWidth: Int, newHeight: Int): ImageBitmap {
        val resized = ImageBitmap(newWidth, newHeight)
        val canvas = Canvas(resized)
        val paint = Paint()

        // draw original into new canvas scaled
        canvas.drawImageRect(
            image = image,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(0, 0),
            dstSize = IntSize(newWidth, newHeight),
            paint = paint
        )

        return resized
    }

    fun resizeBilinear(newWidth: Int, newHeight: Int): ImageBitmap {
        val src = image.toPixelMap()
        val dst = IntArray(newWidth * newHeight)

        val xRatio = (src.width - 1).toFloat() / newWidth
        val yRatio = (src.height - 1).toFloat() / newHeight

        for (y in 0 until newHeight) {
            val srcY = y * yRatio
            val yL = srcY.toInt()
            val yH = minOf(yL + 1, src.height - 1)
            val yWeight = srcY - yL

            for (x in 0 until newWidth) {
                val srcX = x * xRatio
                val xL = srcX.toInt()
                val xH = minOf(xL + 1, src.width - 1)
                val xWeight = srcX - xL

                val c00 = src[xL, yL].value.toInt()
                val c10 = src[xH, yL].value.toInt()
                val c01 = src[xL, yH].value.toInt()
                val c11 = src[xH, yH].value.toInt()

                fun Int.a() = (this shr 24) and 0xFF
                fun Int.r() = (this shr 16) and 0xFF
                fun Int.g() = (this shr 8) and 0xFF
                fun Int.b() = this and 0xFF

                fun interp(c00: Int, c10: Int, c01: Int, c11: Int,
                           xWeight: Float, yWeight: Float,
                           sel: (Int) -> Int): Int {
                    val top = sel(c00) * (1 - xWeight) + sel(c10) * xWeight
                    val bottom = sel(c01) * (1 - xWeight) + sel(c11) * xWeight
                    return ((top * (1 - yWeight) + bottom * yWeight)).toInt()
                }

                val a = interp(c00, c10, c01, c11, xWeight, yWeight) { it.a() }
                val r = interp(c00, c10, c01, c11, xWeight, yWeight) { it.r() }
                val g = interp(c00, c10, c01, c11, xWeight, yWeight) { it.g() }
                val b = interp(c00, c10, c01, c11, xWeight, yWeight) { it.b() }

                dst[y * newWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return createImageBitmapFromPixels(newWidth, newHeight, dst)
    }


    fun resizeBilinearFit(maxWidth: Int, maxHeight: Int): ImageBitmap {
        val aspect = image.width.toFloat() / image.height.toFloat()

        val targetWidth: Int
        val targetHeight: Int

        if (image.width > image.height) {
            // landscape → limit by width
            targetWidth = maxWidth
            targetHeight = (maxWidth / aspect).toInt()
        } else {
            // portrait/square → limit by height
            targetHeight = maxHeight
            targetWidth = (maxHeight * aspect).toInt()
        }

        return resizeBilinear(targetWidth, targetHeight)
    }

    fun resizeBilinearFill(boxWidth: Int, boxHeight: Int): ImageBitmap {
        val aspect = image.width.toFloat() / image.height.toFloat()
        val targetAspect = boxWidth.toFloat() / boxHeight.toFloat()

        val targetWidth: Int
        val targetHeight: Int

        if (aspect > targetAspect) {
            // source is wider → match height, crop width
            targetHeight = boxHeight
            targetWidth = (boxHeight * aspect).toInt()
        } else {
            // source is taller → match width, crop height
            targetWidth = boxWidth
            targetHeight = (boxWidth / aspect).toInt()
        }

        val scaled = this.resizeBilinear(targetWidth, targetHeight)

        // crop center
        val xOffset = (scaled.width - boxWidth) / 2
        val yOffset = (scaled.height - boxHeight) / 2

        val cropped = ImageBitmap(boxWidth, boxHeight)
        val canvas = androidx.compose.ui.graphics.Canvas(cropped)
        val paint = androidx.compose.ui.graphics.Paint()

        canvas.drawImageRect(
            image = scaled,
            srcOffset = IntOffset(xOffset, yOffset),
            srcSize = IntSize(boxWidth, boxHeight),
            dstSize = IntSize(boxWidth, boxHeight),
            paint = paint
        )

        return cropped
    }

    private fun createImageBitmapFromPixels(
        width: Int,
        height: Int,
        pixels: IntArray
    ): ImageBitmap {
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        bitmap.installPixels(pixels.toByteArrayBGRA())
        return bitmap.asImageBitmap()
    }

    private fun IntArray.toByteArrayBGRA(): ByteArray {
        val out = ByteArray(size * 4)
        for (i in indices) {
            val c = this[i]
            val a = (c shr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val base = i * 4
            out[base + 0] = b.toByte()
            out[base + 1] = g.toByte()
            out[base + 2] = r.toByte()
            out[base + 3] = a.toByte()
        }
        return out
    }
}