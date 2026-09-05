package cl.ufchile.app.ui.value

import android.content.Context
import android.content.Intent
import cl.ufchile.app.core.format.Fmt

/**
 * Turns the "today" card into a plain-text message for the system share sheet.
 *
 * Plain text rather than an image: it stays selectable, quotable and
 * searchable wherever it lands. The *asterisk* emphasis renders as bold in
 * WhatsApp and Telegram and is harmless everywhere else.
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

        if (ui.future.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("*Próximos valores ya publicados*")
            ui.future.take(8).forEach {
                sb.appendLine("${Fmt.shortDate(it.date)}   ${Fmt.clpExact(it.value)}")
            }
        }

        sb.appendLine()
        sb.append("Fuente: ${ui.sync.source?.label ?: "datos guardados"}")
        return sb.toString()
    }

    /**
     * Hands the message to the system share sheet. The user picks the app and
     * presses send there; nothing leaves the device on its own.
     */
    fun share(context: Context, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Valor de la UF")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir el valor de la UF"))
    }
}
