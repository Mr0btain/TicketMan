package org.mr0btain.ticketman.components.print

import kotlin.math.abs

/**
 * A QR code as a square of dark and light modules, made by [QrCode.encode]. Byte mode with medium error correction,
 * which covers ticket numbers and short links easily. Up to version 10 (213 bytes)
 */
class QrCode private constructor(
    val size: Int,
    private val modules: Array<BooleanArray>
) {
    /** Whether the module at column [x], row [y] is dark */
    operator fun get(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        private const val MAX_VERSION = 10

        // Medium error correction, by version (index 0 unused)
        private val ECC_CODEWORDS_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
        private val NUM_BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

        // Format info bits for medium error correction
        private const val ECC_FORMAT_BITS = 0

        /**
         * Encodes [text] as UTF-8 in the smallest version it fits
         * @throws IllegalArgumentException if it's too long for version 10
         */
        fun encode(text: String): QrCode {
            val data = text.encodeToByteArray()

            val version = (1..MAX_VERSION).firstOrNull { version ->
                4 + charCountBits(version) + data.size * 8 <= numDataCodewords(version) * 8
            } ?: throw IllegalArgumentException("Text is too long for a QR code: ${data.size} bytes")

            return Builder(version).build(addEccAndInterleave(dataCodewords(data, version), version))
        }

        private fun charCountBits(version: Int) = if (version < 10) 8 else 16

        // Byte mode header, the data, then terminator and padding up to the version's capacity
        private fun dataCodewords(data: ByteArray, version: Int): ByteArray {
            val bits = BitBuffer()
            bits.append(0b0100, 4)
            bits.append(data.size, charCountBits(version))
            data.forEach { bits.append(it.toInt() and 0xFF, 8) }

            val capacity = numDataCodewords(version) * 8
            bits.append(0, minOf(4, capacity - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)

            var pad = 0xEC
            while (bits.size < capacity) {
                bits.append(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }

            return bits.toBytes()
        }

        // Modules left for data and error correction once the fixed patterns are drawn
        private fun numRawDataModules(version: Int): Int {
            var result = (16 * version + 128) * version + 64

            if (version >= 2) {
                val numAlign = version / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (version >= 7) result -= 36
            }

            return result
        }

        private fun numDataCodewords(version: Int): Int =
            numRawDataModules(version) / 8 - ECC_CODEWORDS_PER_BLOCK[version] * NUM_BLOCKS[version]

        // Splits the data into blocks, adds Reed-Solomon error correction to each, and interleaves them
        private fun addEccAndInterleave(data: ByteArray, version: Int): ByteArray {
            val numBlocks = NUM_BLOCKS[version]
            val blockEccLength = ECC_CODEWORDS_PER_BLOCK[version]
            val rawCodewords = numRawDataModules(version) / 8
            val numShortBlocks = numBlocks - rawCodewords % numBlocks
            val shortBlockLength = rawCodewords / numBlocks

            val divisor = reedSolomonDivisor(blockEccLength)
            val blocks = mutableListOf<ByteArray>()
            var offset = 0

            for (i in 0 until numBlocks) {
                val dataLength = shortBlockLength - blockEccLength + if (i < numShortBlocks) 0 else 1
                val blockData = data.copyOfRange(offset, offset + dataLength)
                offset += dataLength

                val ecc = reedSolomonRemainder(blockData, divisor)

                // Short blocks get a placeholder so every block is the same length for interleaving
                val padded = if (i < numShortBlocks) blockData + byteArrayOf(0) else blockData
                blocks.add(padded + ecc)
            }

            val result = ByteArray(rawCodewords)
            var index = 0

            for (i in blocks[0].indices) {
                for (j in blocks.indices) {
                    if (i != shortBlockLength - blockEccLength || j >= numShortBlocks) {
                        result[index++] = blocks[j][i]
                    }
                }
            }

            return result
        }

        private fun reedSolomonDivisor(degree: Int): ByteArray {
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1

            repeat(degree) {
                for (j in result.indices) {
                    result[j] = gfMultiply(result[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < result.size) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
                root = gfMultiply(root, 0x02)
            }

            return result
        }

        private fun reedSolomonRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
            val result = ByteArray(divisor.size)

            data.forEach { byte ->
                val factor = (byte.toInt() xor result[0].toInt()) and 0xFF
                result.copyInto(result, 0, 1)
                result[result.size - 1] = 0
                for (i in result.indices) {
                    result[i] = (result[i].toInt() xor gfMultiply(divisor[i].toInt() and 0xFF, factor)).toByte()
                }
            }

            return result
        }

        // Multiplying in GF(2^8) with the QR code's polynomial
        private fun gfMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor ((y ushr i) and 1) * x
            }
            return z
        }
    }

    // Draws the fixed patterns, places the data and picks the mask that reads best
    private class Builder(private val version: Int) {
        val size = version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun build(codewords: ByteArray): QrCode {
            drawFunctionPatterns()
            drawCodewords(codewords)

            val mask = (0 until 8).minBy { mask ->
                applyMask(mask)
                drawFormatBits(mask)
                penalty().also { applyMask(mask) }
            }

            applyMask(mask)
            drawFormatBits(mask)

            return QrCode(size, modules)
        }

        private fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }

        private fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }

            drawFinder(3, 3)
            drawFinder(size - 4, 3)
            drawFinder(3, size - 4)

            val positions = alignmentPositions()
            for (i in positions.indices) {
                for (j in positions.indices) {
                    // Skip the three corners the finders already use
                    val corner = (i == 0 && j == 0) || (i == 0 && j == positions.lastIndex) || (i == positions.lastIndex && j == 0)
                    if (!corner) drawAlignment(positions[i], positions[j])
                }
            }

            // Reserves the format areas, real bits go in once the mask is known
            drawFormatBits(0)
            drawVersion()
        }

        private fun drawFinder(x: Int, y: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val distance = maxOf(abs(dx), abs(dy))
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until size && yy in 0 until size) set(xx, yy, distance != 2 && distance != 4)
                }
            }
        }

        private fun drawAlignment(x: Int, y: Int) {
            for (dy in -2..2) {
                for (dx in -2..2) set(x + dx, y + dy, maxOf(abs(dx), abs(dy)) != 1)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (version == 1) return IntArray(0)

            val numAlign = version / 7 + 2
            val step = (version * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
            val result = IntArray(numAlign)
            result[0] = 6

            var position = size - 7
            for (i in numAlign - 1 downTo 1) {
                result[i] = position
                position -= step
            }

            return result
        }

        private fun drawFormatBits(mask: Int) {
            val data = ECC_FORMAT_BITS shl 3 or mask
            var remainder = data
            repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
            val bits = (data shl 10 or remainder) xor 0x5412

            for (i in 0..5) set(8, i, bit(bits, i))
            set(8, 7, bit(bits, 6))
            set(8, 8, bit(bits, 7))
            set(7, 8, bit(bits, 8))
            for (i in 9 until 15) set(14 - i, 8, bit(bits, i))

            for (i in 0 until 8) set(size - 1 - i, 8, bit(bits, i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(bits, i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (version < 7) return

            var remainder = version
            repeat(12) { remainder = (remainder shl 1) xor ((remainder ushr 11) * 0x1F25) }
            val bits = version shl 12 or remainder

            for (i in 0 until 18) {
                val dark = bit(bits, i)
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, dark)
                set(b, a, dark)
            }
        }

        // Zig zags up and down two columns at a time from the bottom right, skipping the fixed patterns
        private fun drawCodewords(codewords: ByteArray) {
            var index = 0
            var right = size - 1

            while (right >= 1) {
                if (right == 6) right = 5

                for (vertical in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vertical else vertical

                        if (!isFunction[y][x] && index < codewords.size * 8) {
                            modules[y][x] = bit(codewords[index ushr 3].toInt(), 7 - (index and 7))
                            index++
                        }
                    }
                }

                right -= 2
            }
        }

        // Flips data modules in one of the eight standard patterns, calling it twice undoes it
        private fun applyMask(mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }

                    if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x]
                }
            }
        }

        // Lower is easier to scan: long runs, solid 2x2 blocks and lopsided dark/light counts all add up
        private fun penalty(): Int {
            var result = 0

            fun runs(line: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (line(i) == line(i - 1)) {
                        run++
                        if (run == 5) result += 3 else if (run > 5) result++
                    } else run = 1
                }
            }

            for (y in 0 until size) runs { modules[y][it] }
            for (x in 0 until size) runs { modules[it][x] }

            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val color = modules[y][x]
                    if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) result += 3
                }
            }

            val dark = modules.sumOf { row -> row.count { it } }
            val total = size * size
            result += (abs(dark * 20 - total * 10) + total - 1) / total * 10

            return result
        }

        private fun bit(value: Int, index: Int) = ((value ushr index) and 1) != 0
    }

    private class BitBuffer {
        private val bits = mutableListOf<Boolean>()
        val size get() = bits.size

        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
        }

        fun toBytes(): ByteArray = ByteArray(bits.size / 8) { index ->
            var byte = 0
            for (i in 0 until 8) byte = byte shl 1 or if (bits[index * 8 + i]) 1 else 0
            byte.toByte()
        }
    }
}
