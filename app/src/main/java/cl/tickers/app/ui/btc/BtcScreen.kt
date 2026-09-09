package cl.tickers.app.ui.btc

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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.core.share.shareText
import cl.tickers.app.domain.engine.BtcEngine
import cl.tickers.app.domain.engine.ConverterEngine
import cl.tickers.app.domain.engine.Horizon
import cl.tickers.app.domain.engine.StampKind
import cl.tickers.app.domain.engine.stampKind
import cl.tickers.app.ui.appViewModel
import cl.tickers.app.ui.components.AppCard
import cl.tickers.app.ui.components.HistoryChartCard
import cl.tickers.app.ui.components.KeyValueRow
import cl.tickers.app.ui.components.NumberField
import cl.tickers.app.ui.components.Pill
import cl.tickers.app.ui.components.ScreenHeader
import cl.tickers.app.ui.components.SectionTitle
import cl.tickers.app.ui.value.UfViewModel
import java.math.BigDecimal
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check

/** How long a spot price is shown as current before it is called stale. */
private const val FRESH_MS = 60_000L

@Composable
fun BtcScreen() {
    val context = LocalContext.current
    val vm = appViewModel { BtcViewModel(context.applicationContext, it.btcSource) }
    // The UF screen's model already holds the daily series and the indicators,
    // and the dollar is one of those. Fetching it twice would be two answers to
    // one question.
    val ufVm = appViewModel { UfViewModel(it.ufRepository, it.settings) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ufUi by ufVm.ui.collectAsStateWithLifecycle()

    val dollar = ufUi.indicators.firstOrNull { it.code == "dolar" }
    val uf = ufUi.current?.value
    val usd = ui.usd

    val clp = if (usd != null && dollar != null) {
        BtcEngine.btcUsdToClp(usd, dollar.value)
    } else {
        null
    }
    val inUf = if (usd != null && dollar != null && uf != null) {
        BtcEngine.btcUsdToUf(usd, dollar.value, uf)
    } else {
        null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "Bitcoin") }

        item {
            AppCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            ui.error != null -> ui.error!!.headline
                            ui.usd == null -> "Cargando…"
                            System.currentTimeMillis() - ui.fetchedAt > FRESH_MS -> "Hace un momento"
                            else -> "Ahora"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (usd != null) {
                        IconButton(onClick = {
                            shareText(
                                context = context,
                                subject = "Precio del bitcoin",
                                message = buildShare(
                                    usd, clp, inUf,
                                    dollar?.date?.let(Fmt::dayMonth), ui.source,
                                ),
                                chooserTitle = "Compartir el precio del bitcoin",
                            )
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Compartir el precio del bitcoin")
                        }
                    }
                }

                if (ui.error != null && usd == null) {
                    Text(
                        ui.error!!.hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        usd?.let(Fmt::usd) ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    clp?.let {
                        Text(Fmt.clp(it), style = MaterialTheme.typography.headlineSmall)
                    }
                    inUf?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            Fmt.uf(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ui.source.isNotEmpty()) Pill(ui.source)
                        dollar?.let { Pill("Dólar del ${Fmt.dayMonth(it.date)}") }
                    }
                }
            }
        }

        item {
            HistoryChartCard(
                ranges = Horizon.entries,
                range = ui.horizon,
                onRange = vm::onHorizon,
                rangeLabel = { it.label },
                points = ui.candles,
                stamp = { stamp(it.at, ui.horizon) },
                amount = { Fmt.usd(it.usd) },
                summary = ui.chartSummary,
                scrubIndex = ui.scrubIndex,
                onScrub = vm::onScrub,
                loading = ui.chartLoading,
            )
        }

        if (usd != null && dollar != null) {
            // The card opens on one bitcoin rather than empty, so it answers
            // before anyone types in it. Computed here rather than stored,
            // because it depends on a price that arrives after the screen does.
            val opening = if (ui.btcText == "1") {
                ConverterEngine.btcConvert(
                    ConverterEngine.BtcField.BTC, BigDecimal.ONE, usd, dollar.value,
                )
            } else {
                null
            }
            val usdShown = ui.usdText.ifEmpty {
                opening?.usd?.toPlainString()?.replace('.', ',').orEmpty()
            }
            val clpShown = ui.clpText.ifEmpty { opening?.clp?.toPlainString().orEmpty() }

            item {
                AppCard {
                    SectionTitle("Conversor")
                    NumberField(
                        value = ui.btcText,
                        onValueChange = { vm.onBtcInput(it, usd, dollar.value) },
                        label = "Bitcoin",
                        suffix = "BTC",
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = usdShown,
                        onValueChange = { vm.onUsdInput(it, usd, dollar.value) },
                        label = "Dólares",
                        suffix = "USD",
                        action = {
                            CopyValueButton(
                                label = "Copiar el precio en dólares",
                                text = convShare(ui.btcText, usdShown, null),
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        value = clpShown,
                        onValueChange = { vm.onClpInput(it, usd, dollar.value) },
                        label = "Pesos",
                        suffix = "CLP",
                        allowDecimals = false,
                        // The dollars above are the market's own price; only the
                        // pesos pass through a rate with a publication day, so
                        // only they carry it.
                        supporting = "Dólar observado del ${Fmt.dayMonth(dollar.date)}",
                        action = {
                            CopyValueButton(
                                label = "Copiar el precio en dólares y pesos",
                                text = convShare(ui.btcText, usdShown, clpShown),
                            )
                        },
                    )
                    inUf?.let {
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow("Un bitcoin en UF", Fmt.uf(it))
                    }
                }
            }
        }

        item {
            Text(
                "Precio de mercado en dólares, convertido a pesos con el dólar " +
                    "observado que publica el Banco Central una vez por día hábil. " +
                    "No es una cotización de compra ni de venta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** How a candle's moment is written: the domain says how precise, this says how it reads. */
private fun stamp(at: Long, horizon: Horizon): String = when (stampKind(horizon)) {
    StampKind.TIME -> Fmt.timeHm(at)
    StampKind.DAY -> Fmt.dayMonthNum(at)
    StampKind.MONTH -> Fmt.monthYearOf(at)
}

private fun buildShare(
    usd: BigDecimal,
    clp: BigDecimal?,
    inUf: BigDecimal?,
    dollarDay: String?,
    source: String,
): String = buildList<String> {
    add("*Bitcoin*")
    add(Fmt.usd(usd))
    clp?.let { add(Fmt.clp(it)) }
    inUf?.let { add(Fmt.uf(it)) }
    add("")
    if (source.isNotEmpty()) add("Fuente: $source")
    dollarDay?.let { add("Dólar observado del $it") }
}.joinToString("\n")

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

/**
 * What the copy buttons send. Deliberately one line: it is meant to be pasted
 * into a conversation, not read as a report.
 */
private fun convShare(btcText: String, usdText: String, clpText: String?): String {
    val amount = Fmt.parseNumber(btcText)
    val head = if (amount?.compareTo(BigDecimal.ONE) == 0) "Bitcoin de hoy" else "$btcText BTC"
    return buildList<String> {
        add(head)
        add(Fmt.usd(Fmt.parseNumber(usdText) ?: BigDecimal.ZERO))
        Fmt.parseNumber(clpText.orEmpty())?.let { add("CLP" + Fmt.clp(it)) }
    }.joinToString(", ")
}
