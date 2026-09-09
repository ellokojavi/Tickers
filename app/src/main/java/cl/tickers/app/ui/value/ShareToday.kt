package cl.tickers.app.ui.value

import android.content.Context
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.core.share.shareText

/**
 * Turns the "today" card into a plain-text message for the share sheet: what
 * is on the card and nothing more. The future values live in their own card
 * further down and are not this card's to send.
 */
object ShareToday {

    fun buildMessage(ui: UfUiState): String {
        val current = ui.current ?: return "Sin datos de la UF."
        val sb = StringBuilder()

        sb.appendLine("*UF de hoy*")
        sb.appendLine(Fmt.longDate(current.date))
        sb.appendLine(Fmt.clpExact(current.value))

        val deltas = buildList {
            ui.dailyDelta?.let { add("${Fmt.clpSigned(it)} vs. ayer") }
            ui.monthDeltaPct?.let { add("${Fmt.pctSigned(it)} en 30 días") }
        }
        if (deltas.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(deltas.joinToString("  ·  "))
        }

        sb.appendLine()
        sb.append("Fuente: ${ui.sync.source?.label ?: "datos guardados"}")
        return sb.toString()
    }

    fun share(context: Context, message: String) = shareText(
        context = context,
        subject = "Valor de la UF",
        message = message,
        chooserTitle = "Compartir el valor de la UF",
    )
}
