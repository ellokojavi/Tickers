package cl.tickers.app.ui.value

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.tickers.app.R
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.data.prefs.ThemeMode
import cl.tickers.app.domain.engine.LookupResult
import cl.tickers.app.domain.engine.UfLookup
import cl.tickers.app.domain.model.DataSource
import cl.tickers.app.ui.about.AboutDialog
import cl.tickers.app.ui.appViewModel
import cl.tickers.app.ui.components.AppCard
import cl.tickers.app.ui.components.HistoryChartCard
import cl.tickers.app.ui.components.KeyValueRow
import cl.tickers.app.ui.components.toUtcDate
import cl.tickers.app.ui.components.toUtcMillis
import cl.tickers.app.ui.components.NumberField
import cl.tickers.app.ui.components.Pill
import cl.tickers.app.ui.components.ScreenHeader
import cl.tickers.app.ui.components.SectionTitle
import cl.tickers.app.ui.components.signColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check

/**
 * The single "Valor UF" destination.
 *
 * Today's value and the historical series answer the same question at
 * different points in time, so they share one screen. The default state is
 * exactly the at-a-glance view; the historical controls expand in place inside
 * the chart card and are not persisted, so every launch opens on today.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UfValueScreen(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    onToggleTheme: () -> Unit = {},
) {
    val vm = appViewModel { UfViewModel(it.ufRepository, it.settings) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Unidad de Fomento") {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        when (themeMode) {
                            ThemeMode.LIGHT -> Icons.Outlined.LightMode
                            ThemeMode.DARK -> Icons.Outlined.DarkMode
                        },
                        contentDescription = "Cambiar tema",
                    )
                }
                IconButton(onClick = vm::refresh) {
                    if (ui.refreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                }
            }
        }

        item {
            HeroCard(
                ui = ui,
                onShare = { ShareToday.share(context, ShareToday.buildMessage(ui)) },
                onSourceClick = { showAbout = true },
            )
        }

        ui.error?.let { message ->
            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Conversor")
                NumberField(
                    ui.ufText,
                    vm::onUfInput,
                    // The date belongs to the UF, which is the value everything
                    // else on this card is derived from. It was on the peso
                    // field, where it read as if the peso had a publication day.
                    label = ui.current?.let { "UF (al ${Fmt.dayMonth(it.date)})" } ?: "UF",
                    suffix = "UF",
                )
                Spacer(Modifier.height(10.dp))
                NumberField(
                    ui.clpText,
                    vm::onClpInput,
                    label = "Pesos",
                    suffix = "CLP",
                    allowDecimals = false,
                    action = {
                        ShareValueButton(
                            label = "Copiar el valor en pesos",
                            text = converterShare(ui.ufText, ui.clpText, null),
                        )
                    },
                )
                ui.dollar?.let { dollar ->
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        ui.usdText,
                        vm::onUsdInput,
                        label = "Dólares",
                        suffix = "USD",
                        supporting = "Dólar observado del ${Fmt.dayMonth(dollar.date)}",
                        action = {
                            ShareValueButton(
                                label = "Copiar el valor en pesos y dólares",
                                text = converterShare(ui.ufText, ui.clpText, ui.usdText),
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
                onRange = vm::setRange,
                rangeLabel = { it.label },
                points = ui.chartValues,
                stamp = { Fmt.shortDate(it.date) },
                amount = { Fmt.clpExact(it.value) },
                summary = ui.chartSummary,
                scrubIndex = ui.scrubIndex,
                onScrub = vm::scrub,
                loading = ui.loadingOlder,
                message = ui.historyMessage,
            )
        }

        item { LookupCard(ui, onChangeDate = { showPicker = true }) }

        if (ui.future.isNotEmpty()) {
            item { FutureCard(ui) }
        }

        if (ui.indicators.isNotEmpty()) {
            item { IndicatorsCard(ui) }
        }

        // The day-by-day list runs to hundreds of rows, so it stays folded away
        // until asked for.
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = vm::toggleDetail)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Detalle diario")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ui.detailExpanded) "Ocultar" else "${Fmt.integer(ui.rangeValues.size)} días",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        if (ui.detailExpanded) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                        contentDescription = if (ui.detailExpanded)
                            "Ocultar el detalle diario" else "Mostrar el detalle diario",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (ui.detailExpanded) {
            items(ui.rangeValues.reversed().take(DETAIL_ROWS), key = { it.date.toString() }) { v ->
                KeyValueRow(
                    label = Fmt.shortDate(v.date) +
                        if (v.date.isAfter(LocalDate.now())) "  ·  futuro publicado" else "",
                    value = Fmt.clpExact(v.value),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (ui.rangeValues.size > DETAIL_ROWS) {
                item {
                    Text(
                        "Se muestran los ${Fmt.integer(DETAIL_ROWS)} días más recientes del período.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // A quiet footer rather than a banner or a dialog on launch: the notice
        // has to be findable without ever getting in the way.
        item { Footer(onAbout = { showAbout = true }) }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    if (showPicker) {
        // The horizon is a hard bound, not a warning: a day with no published
        // value cannot be picked in the first place.
        val maxDate = ui.lastPublished ?: LocalDate.now()
        val minDate = UfLookup.SERIES_START
        val state = rememberDatePickerState(
            initialSelectedDateMillis = ui.lookupDate.toUtcMillis(),
            yearRange = minDate.year..maxDate.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = utcTimeMillis.toUtcDate()
                    return !d.isBefore(minDate) && !d.isAfter(maxDate)
                }

                override fun isSelectableYear(year: Int): Boolean =
                    year in minDate.year..maxDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { vm.lookup(it.toUtcDate()) }
                        showPicker = false
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = state) }
    }
}


@Composable
private fun HeroCard(ui: UfUiState, onShare: () -> Unit, onSourceClick: () -> Unit) {
    AppCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ui.current?.let { Fmt.longDate(it.date) } ?: "Cargando…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ui.current != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Compartir el valor de la UF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            ui.current?.let { Fmt.clpExact(it.value) } ?: "—",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.dailyDelta?.let { delta ->
                Text(
                    "${Fmt.clpSigned(delta)} vs. ayer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = signColor(delta),
                )
            }
            ui.monthDeltaPct?.let { pct ->
                Text(
                    "${Fmt.pctSigned(pct)} en 30 días",
                    style = MaterialTheme.typography.bodyMedium,
                    color = signColor(pct),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                text = ui.sync.source?.label ?: DataSource.CACHE.label,
                color = if (ui.sync.source?.official == true)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                container = if (ui.sync.source?.official == true)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                // Where the number came from is also where the attribution and
                // the independence notice live.
                onClick = onSourceClick,
            )
            ui.current?.let {
                Pill("Actualizado ${Fmt.relativeDay(it.date)}", icon = Icons.Outlined.Schedule)
            }
        }
    }
}

@Composable
private fun LookupCard(ui: UfUiState, onChangeDate: () -> Unit) {
    AppCard {
        SectionTitle("Consultar una fecha")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Fmt.longDate(ui.lookupDate), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onChangeDate) { Text("Cambiar") }
        }
        Spacer(Modifier.height(10.dp))

        when (val result = ui.lookup) {
            is LookupResult.Exact -> {
                Text(
                    Fmt.clpExact(result.value.value),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (result.isFuture) {
                    Pill(
                        text = "Valor oficial ya publicado",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Text(
                        "Valor publicado para esa fecha.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is LookupResult.Nearest -> {
                Text(
                    Fmt.clpExact(result.value.value),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sin dato para el ${Fmt.shortDate(result.requested)}. Se muestra el " +
                        "${Fmt.shortDate(result.value.date)}, el día publicado más cercano " +
                        "anterior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is LookupResult.NotPublishedYet -> {
                Text(
                    "Sin valor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    result.lastPublished?.let {
                        "La UF de esa fecha todavía no se publica. El último valor oficial " +
                            "es el del ${Fmt.shortDate(it)}; el siguiente tramo se publica " +
                            "el día 9 del mes que viene."
                    } ?: "Todavía no hay valores publicados para esa fecha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is LookupResult.BeforeCoverage -> {
                Text(
                    "Sin valor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "La serie diaria de la UF parte el ${Fmt.shortDate(result.earliest)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LookupResult.Unavailable -> {
                Text(
                    "Sin datos",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No se pudo descargar ese período. Revisa tu conexión y vuelve a " +
                        "intentar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LookupResult.Loading -> Text(
                "Buscando…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ui.lastPublished?.let { last ->
            Spacer(Modifier.height(12.dp))
            Text(
                "Puedes consultar entre el ${Fmt.shortDate(UfLookup.SERIES_START)} y el " +
                    "${Fmt.shortDate(last)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun FutureCard(ui: UfUiState) {
    AppCard {
        SectionTitle("Valores futuros ya publicados")
        Text(
            "La UF se reajusta a diario entre el 10 de un mes y el 9 del siguiente, " +
                "según el IPC del mes anterior. Estos valores son oficiales, no proyecciones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        ui.future.take(12).forEach { v ->
            KeyValueRow(label = Fmt.shortDate(v.date), value = Fmt.clpExact(v.value))
        }
        if (ui.future.size > 12) {
            Text(
                "y ${Fmt.integer(ui.future.size - 12)} días más hasta el ${Fmt.shortDate(ui.future.last().date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun IndicatorsCard(ui: UfUiState) {
    AppCard {
        SectionTitle("Otros indicadores")
        ui.indicators.forEachIndexed { index, ind ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            KeyValueRow(
                label = ind.name,
                value = if (ind.unit.equals("Porcentaje", true))
                    Fmt.pct1(ind.value) else Fmt.clpExact(ind.value),
            )
        }
    }
}

/** How many day rows the detail list renders before it stops. */
private const val DETAIL_ROWS = 400

@Composable
private fun Footer(onAbout: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "App independiente, sin relación con la CMF, el Banco Central ni el INE.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onAbout) {
            Text("Acerca de y fuentes", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Copies one figure straight to the clipboard, for pasting into a message.
 *
 * A share sheet is the right gesture for the whole card at the top of the
 * screen; for a single number inside a field it is three taps to do what one
 * should. Android has no equivalent of the web's "no share sheet at all", so
 * this is the same choice made for a different reason: the unit here is one
 * value, not a report.
 */
@Composable
private fun ShareValueButton(label: String, text: String) {
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text))
            copied = true
        },
    ) {
        Icon(
            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
            contentDescription = if (copied) "Copiado" else label,
        )
    }

    // Android 13 and up shows its own copy confirmation, so a second one would
    // be two notices for one action; below that the icon is the only feedback.
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
private fun converterShare(ufText: String, clpText: String, usdText: String?): String {
    val amount = Fmt.parseNumber(ufText)
    val head = if (amount?.compareTo(java.math.BigDecimal.ONE) == 0) {
        "UF de hoy"
    } else {
        Fmt.uf(amount ?: java.math.BigDecimal.ZERO)
    }
    val clp = Fmt.parseNumber(clpText) ?: java.math.BigDecimal.ZERO
    return buildList<String> {
        add(head)
        add("CLP" + Fmt.clp(clp))
        Fmt.parseNumber(usdText.orEmpty())?.let { add(Fmt.usd(it)) }
    }.joinToString(", ")
}
