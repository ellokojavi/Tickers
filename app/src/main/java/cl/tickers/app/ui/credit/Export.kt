package cl.tickers.app.ui.credit

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.domain.model.MortgageResult
import java.io.File
import java.math.BigDecimal

/**
 * CSV export of an amortisation schedule.
 *
 * Uses ";" as the separator and "," as the decimal mark, which is what Excel
 * expects under a Chilean locale. A comma separator would split every amount.
 */
object ScheduleExport {

    fun buildCsv(name: String, result: MortgageResult, ufValue: BigDecimal?): String {
        val sb = StringBuilder()
        sb.appendLine("Simulacion;$name")
        sb.appendLine("Monto del credito;${dec(result.loanAmountUf)};UF")
        sb.appendLine("Tasa anual;${dec(result.input.annualRatePct)};%")
        sb.appendLine("Convencion;${result.input.rateConvention.label}")
        sb.appendLine("Plazo;${result.input.termYears};anios")
        sb.appendLine("Primer vencimiento;${Fmt.shortDate(result.input.firstPaymentDate)}")
        sb.appendLine("Dividendo (capital+interes);${dec(result.basePaymentUf)};UF")
        result.caePct?.let { sb.appendLine("CAE;${dec(it)};%") }
        ufValue?.let { sb.appendLine("Valor UF usado;${dec(it)};CLP") }
        sb.appendLine()
        sb.appendLine(
            listOf(
                "N", "Fecha", "Saldo inicial UF", "Interes UF", "Amortizacion UF",
                "Dividendo UF", "Desgravamen UF", "Incendio UF", "Prepago UF",
                "Total mes UF", "Total mes CLP", "Saldo final UF",
            ).joinToString(";")
        )
        result.schedule.forEach { row ->
            sb.appendLine(
                listOf(
                    row.number.toString(),
                    Fmt.shortDate(row.date),
                    dec(row.openingBalanceUf),
                    dec(row.interestUf),
                    dec(row.principalUf),
                    dec(row.paymentUf),
                    dec(row.lifeInsuranceUf),
                    dec(row.fireInsuranceUf),
                    dec(row.prepaymentUf),
                    dec(row.totalOutflowUf),
                    ufValue?.let { dec(row.totalOutflowUf.multiply(it).setScale(0, java.math.RoundingMode.HALF_UP)) } ?: "",
                    dec(row.closingBalanceUf),
                ).joinToString(";")
            )
        }
        return sb.toString()
    }

    fun share(context: Context, fileName: String, csv: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, sanitize(fileName)).apply { writeText(csv, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir tabla de pagos"))
    }

    private fun dec(v: BigDecimal): String = v.toPlainString().replace('.', ',')

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "tabla.csv" }
}
