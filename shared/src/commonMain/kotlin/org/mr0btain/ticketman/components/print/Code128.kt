package org.mr0btain.ticketman.components.print

/**
 * Encodes text as a Code 128 barcode, what a one dimensional scanner at the counter reads. Text of an even number
 * of digits uses code set C (two digits a symbol, so a 12 digit code stays short); anything else printable in
 * ASCII uses code set B. In house like [QrCode], so there's no library to carry on every platform
 */
object Code128 {
    /** The quiet zone to leave each side, in modules, as the spec asks */
    const val QUIET_ZONE = 10

    /**
     * The barcode as module widths, bar first then space, alternating, ending on the stop's last bar
     * @return Null when [text] is empty, too long, or has a character code set B can't hold
     */
    fun encode(text: String): List<Int>? {
        if (text.isEmpty() || text.length > MAX_LENGTH) return null

        val values = mutableListOf<Int>()
        if (text.length % 2 == 0 && text.length >= 4 && text.all { it in '0'..'9' }) {
            values += START_C
            text.chunked(2).forEach { values += it.toInt() }
        } else {
            if (text.any { it.code !in 32..126 }) return null
            values += START_B
            text.forEach { values += it.code - 32 }
        }

        // The check symbol: the start's value plus each symbol's value times its place, mod 103
        val check = values.drop(1).foldIndexed(values.first()) { index, sum, value -> sum + value * (index + 1) } % 103
        values += check

        return values.flatMap { PATTERNS[it] } + PATTERNS[STOP]
    }

    /** Width in modules of [widths], without the quiet zones */
    fun modules(widths: List<Int>): Int = widths.sum()

    private const val MAX_LENGTH = 48
    private const val START_B = 104
    private const val START_C = 105
    private const val STOP = 106

    /** Bar and space widths of each symbol value, from the spec's table. The stop has seven */
    val PATTERNS: List<List<Int>> = listOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    ).map { pattern -> pattern.map { it - '0' } }
}
