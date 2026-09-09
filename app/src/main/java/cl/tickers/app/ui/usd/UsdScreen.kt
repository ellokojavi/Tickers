package cl.tickers.app.ui.usd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.core.share.shareText
import cl.tickers.app.ui.appViewModel
import cl.tickers.app.ui.components.AppCard
import cl.tickers.app.ui.components.HistoryChartCard
import cl.tickers.app.ui.components.NumberField
import cl.tickers.app.ui.components.Pill
import cl.tickers.app.ui.components.ScreenHeader
import cl.tickers.app.ui.components.SectionTitle
import cl.tickers.app.ui.components.signColor
import cl.tickers.app.ui.value.Range
import java.math.BigDecimal

@Composable
fun UsdScreen() {
    val context = LocalContext.current
    val vm = appViewModel { UsdViewModel(context.applicationContext) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "Dólar observado") }

        item {
            AppCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ui.current?.let { Fmt.longDate(it.date) }
                            ?: if (ui.loading) "Cargando…" else "Sin datos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ui.current?.let { current ->
                        IconButton(onClick = {
                            shareText(
                                context = context,
                                subject = "Dólar observado",
                                message = buildShare(current, ui.dailyDelta),
                                chooserTitle = "Compartir el valor del dólar",
                            )
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartir el valor del dólar")
                        }
                    }
                }
                Text(
                    ui.current?.let { Fmt.clpExact(it.value) } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                )
                ui.dailyDelta?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${Fmt.clpSigned(it)} vs. la publicación anterior",
                        style = MaterialTheme.typography.bodyMedium,
                        color = signColor(it),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Dólar observado")
                    Pill("Banco Central")
                }
            }
        }

        ui.current?.let { current ->
            item {
                AppCard {
                    SectionTitle("Conversor")
                    NumberField(
                        ui.usdText,
                        vm::onUsdInput,
                        // The date belongs to the rate, which is what the other
                        // field is derived from.
                        label = "Dólares (al ${Fmt.dayMonth(current.date)})",
                        suffix = "USD",
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        ui.clpText,
                        vm::onClpInput,
                        label = "Pesos",
                        suffix = "CLP",
                        allowDecimals = false,
                        action = {
                            CopyValueButton(
                                label = "Copiar el valor en pesos",
                                text = converterShare(ui.usdText, ui.clpText),
                            )
                        },
                    )
                }
            }
        }

        item {
            HistoryChartCard(
                ranges = Range.entries,
                range = ui.range,
                onRange = vm::onRange,
                rangeLabel = { it.label },
                points = ui.chartValues,
                stamp = { Fmt.shortDate(it.date) },
                amount = { Fmt.clpExact(it.value) },
                summary = ui.chartSummary,
                scrubIndex = ui.scrubIndex,
                onScrub = vm::onScrub,
                loading = ui.loading,
            )
        }

        item {
            Text(
                "El dólar observado se publica cada día hábil y refleja las " +
                    "transacciones del día hábil anterior. Los fines de semana y " +
                    "feriados no tienen publicación, y esos días no se inventan: se " +
                    "muestra el último valor publicado, con su fecha.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** See ShareValueButton in the UF screen: one figure, one tap, no share sheet. */
@Composable
private fun CopyValueButton(label: String, text: String) {
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    IconButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }) {
        Icon(
            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = if (copied) "Copiado" else label,
        )
    }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2_000)
            copied = false
        }
    }
}

private fun buildShare(current: cl.tickers.app.domain.model.DatedValue, delta: BigDecimal?): String =
    buildList<String> {
        add("*Dólar observado*")
        add(Fmt.longDate(current.date))
        add(Fmt.clpExact(current.value))
        delta?.let {
            add("")
            add("${Fmt.clpSigned(it)} vs. la publicación anterior")
        }
        add("")
        add("Fuente: Banco Central de Chile")
    }.joinToString("\n")

/**
 * What the copy button sends. Deliberately one line: it is meant to be pasted
 * into a conversation, not read as a report.
 */
private fun converterShare(usdText: String, clpText: String): String {
    val amount = Fmt.parseNumber(usdText)
    val head = if (amount?.compareTo(BigDecimal.ONE) == 0) {
        "Dólar de hoy"
    } else {
        Fmt.usd(amount ?: BigDecimal.ZERO)
    }
    return "$head, CLP" + Fmt.clp(Fmt.parseNumber(clpText) ?: BigDecimal.ZERO)
}
