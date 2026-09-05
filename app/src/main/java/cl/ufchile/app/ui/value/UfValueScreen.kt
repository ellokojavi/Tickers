package cl.ufchile.app.ui.value

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
import androidx.compose.material.icons.outlined.BrightnessAuto
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.ufchile.app.R
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.data.prefs.ThemeMode
import cl.ufchile.app.domain.engine.LookupResult
import cl.ufchile.app.domain.engine.UfLookup
import cl.ufchile.app.domain.model.DataSource
import cl.ufchile.app.ui.appViewModel
import cl.ufchile.app.ui.components.AppCard
import cl.ufchile.app.ui.components.ChipRow
import cl.ufchile.app.ui.components.KeyValueRow
import cl.ufchile.app.ui.components.NumberField
import cl.ufchile.app.ui.components.Pill
import cl.ufchile.app.ui.components.ScreenHeader
import cl.ufchile.app.ui.components.SectionTitle
import cl.ufchile.app.ui.components.Sparkline
import cl.ufchile.app.ui.components.signColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onCycleTheme: () -> Unit = {},
) {
    val vm = appViewModel { UfViewModel(it.ufRepository, it.settings) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Unidad de Fomento") {
                IconButton(onClick = onCycleTheme) {
                    Icon(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
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

        item { HeroCard(ui, onShare = { ShareToday.share(context, ShareToday.buildMessage(ui)) }) }

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
                NumberField(ui.ufText, vm::onUfInput, label = "UF", suffix = "UF")
                Spacer(Modifier.height(10.dp))
                NumberField(ui.clpText, vm::onClpInput, label = "Pesos", suffix = "CLP")
            }
        }

        item { ChartCard(ui, vm) }

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
                        if (ui.detailExpanded) "Ocultar" else "${ui.rangeValues.size} días",
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
            items(ui.rangeValues.reversed().take(400), key = { it.date.toString() }) { v ->
                KeyValueRow(
                    label = Fmt.shortDate(v.date) +
                        if (v.date.isAfter(LocalDate.now())) "  ·  futuro publicado" else "",
                    value = Fmt.clpExact(v.value),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (ui.rangeValues.size > 400) {
                item {
                    Text(
                        "Se muestran los 400 días más recientes del período.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        }
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

/** The Material date picker speaks UTC milliseconds; the app speaks dates. */
private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun HeroCard(ui: UfUiState, onShare: () -> Unit) {
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
            )
            ui.current?.let {
                Pill("Actualizado ${Fmt.relativeDay(it.date)}", icon = Icons.Outlined.Schedule)
            }
        }
    }
}

@Composable
private fun ChartCard(ui: UfUiState, vm: UfViewModel) {
    AppCard {
        SectionTitle("Histórico")

        ChipRow(
            options = Range.entries.toList(),
            selected = ui.range,
            onSelect = vm::setRange,
            label = { it.label },
        )
        if (ui.loadingOlder) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        ui.historyMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // One line above the chart carries either the period summary or, while
        // a finger is down, the day being explored. Below the chart it would
        // fall past the fold.
        val scrubbed = ui.scrubbed
        if (scrubbed != null) {
            Text(
                "${Fmt.shortDate(scrubbed.date)}   ${Fmt.clpExact(scrubbed.value)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val pct = ui.rangeChangePct
                if (pct != null) {
                    Text(
                        "${Fmt.pctSigned(pct)} en el período",
                        style = MaterialTheme.typography.bodyMedium,
                        color = signColor(pct),
                    )
                    ui.rangeAnnualisedPct?.let { annual ->
                        Text(
                            "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            "${Fmt.pctSigned(annual)} anualizado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = signColor(annual),
                        )
                    }
                } else {
                    Text(" ", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        Sparkline(
            values = ui.chartValues,
            height = 200.dp,
            selectedIndex = ui.scrubIndex,
            onScrub = vm::scrub,
        )

        // Both ends are labelled with their value, so the chart can be read
        // without touching it.
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            EndpointLabel(ui.rangeValues.firstOrNull(), alignEnd = false)
            EndpointLabel(ui.rangeValues.lastOrNull(), alignEnd = true)
        }
    }
}

@Composable
private fun EndpointLabel(value: cl.ufchile.app.domain.model.UfValue?, alignEnd: Boolean) {
    if (value == null) return
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            Fmt.shortDate(value.date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Fmt.clpExact(value.value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
                "y ${ui.future.size - 12} días más hasta el ${Fmt.shortDate(ui.future.last().date)}",
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
