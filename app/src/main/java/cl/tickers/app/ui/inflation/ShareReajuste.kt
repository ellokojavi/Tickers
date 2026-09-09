package cl.tickers.app.ui.inflation

import android.content.Context
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.core.share.shareText
import cl.tickers.app.domain.model.ReajusteResult

/**
 * Turns a restatement into a plain-text message for the share sheet.
 *
 * The equivalence itself leads, because that is what gets quoted in a chat;
 * the arithmetic behind it follows for anyone who wants to check it. Only
 * what the result card shows goes in: the sentence about the method belongs
 * to the card below it and stays there.
 */
object ShareReajuste {

    fun buildMessage(result: ReajusteResult): String = buildString {
        appendLine("*Equivalencia de valores*")
        appendLine()
        appendLine("${Fmt.clp(result.amount)} del ${Fmt.longDate(result.from)}")
        appendLine("equivalen a")
        appendLine("${Fmt.clp(result.adjustedAmount)} del ${Fmt.longDate(result.to)}")
        appendLine()
        appendLine("Reajuste: ${Fmt.factor(result.factor)}")
        appendLine("Variación acumulada: ${Fmt.pct(result.variationPct)}")
        appendLine("Equivalente anual: ${Fmt.pct(result.annualisedPct)}")
        appendLine("Período: ${Fmt.period(result.days)}")
        appendLine()
        appendLine("*Cálculo por UF*")
        appendLine("UF el ${Fmt.shortDate(result.from)}: ${Fmt.clpExact(result.ufAtFrom)}")
        appendLine("Equivale a: ${Fmt.uf4(result.ufUnits)}")
        append("UF el ${Fmt.shortDate(result.to)}: ${Fmt.clpExact(result.ufAtTo)}")
    }

    fun share(context: Context, result: ReajusteResult) = shareText(
        context = context,
        subject = "Equivalencia de valores",
        message = buildMessage(result),
        chooserTitle = "Compartir la equivalencia",
    )
}
