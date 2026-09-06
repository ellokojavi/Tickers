package cl.ufchile.app.core.format

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Groups thousands with "." while the user types, keeping "," as the decimal
 * separator, as Chilean numbers are written.
 *
 * The field's own state stays raw ("1234567,89"); only the display is grouped.
 * That matters because the separator is then never something the user has to
 * type: fields used to accept a typed "." and then discard it when parsing, so
 * "4.5" in a rate field silently became 45. Now a typed "." or "," is always
 * the decimal separator — which also covers phones whose numeric keypad offers
 * a dot rather than a comma — and grouping happens on its own.
 */
class ThousandsTransformation(
    private val allowDecimals: Boolean = true,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val negative = raw.startsWith("-")
        val body = if (negative) raw.substring(1) else raw

        val commaAt = if (allowDecimals) body.indexOf(DECIMAL) else -1
        val integerPart = if (commaAt >= 0) body.substring(0, commaAt) else body
        val decimalPart = if (commaAt >= 0) body.substring(commaAt) else ""

        val out = StringBuilder()
        // One entry per cursor position in the raw text, including the end.
        val map = IntArray(raw.length + 1)

        var rawIndex = 0
        if (negative) {
            out.append('-')
            rawIndex = 1
            map[1] = out.length
        }
        for (i in integerPart.indices) {
            if (i > 0 && (integerPart.length - i) % GROUP == 0) out.append(GROUPING)
            out.append(integerPart[i])
            map[rawIndex + i + 1] = out.length
        }
        val afterInteger = rawIndex + integerPart.length
        for (i in decimalPart.indices) {
            out.append(decimalPart[i])
            map[afterInteger + i + 1] = out.length
        }

        return TransformedText(
            AnnotatedString(out.toString()),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    map[offset.coerceIn(0, raw.length)]

                override fun transformedToOriginal(offset: Int): Int {
                    val target = offset.coerceIn(0, out.length)
                    for (o in map.indices) if (map[o] >= target) return o
                    return raw.length
                }
            },
        )
    }

    private companion object {
        const val DECIMAL = ','
        const val GROUPING = '.'
        const val GROUP = 3
    }
}

/**
 * Normalises what a numeric field is allowed to hold: digits, an optional
 * leading minus and at most one decimal separator. Grouping characters are
 * dropped because they are supplied by the display, never typed.
 */
fun sanitizeNumericInput(input: String, allowDecimals: Boolean): String {
    val negative = input.startsWith("-")
    val out = StringBuilder()
    var seenDecimal = false
    for (c in input) {
        when {
            c.isDigit() -> out.append(c)
            allowDecimals && (c == ',' || c == '.') && !seenDecimal && out.isNotEmpty() -> {
                out.append(',')
                seenDecimal = true
            }
        }
    }
    return if (negative) "-$out" else out.toString()
}
