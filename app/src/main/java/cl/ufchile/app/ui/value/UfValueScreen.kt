package cl.ufchile.app.ui.value

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.data.prefs.ThemeMode
import cl.ufchile.app.domain.model.DataSource
import cl.ufchile.app.ui.appViewModel
import cl.ufchile.app.ui.components.AppCard
import cl.ufchile.app.ui.components.ChipRow
import cl.ufchile.app.ui.components.KeyValueRow
import cl.ufchile.app.ui.components.NumberField
import cl.ufchile.app.ui.components.Pill
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
    var showPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Unidad de Fomento", style = MaterialTheme.typography.titleLarge)
                Row {
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
                            CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                        }
                    }
                }
            }
        }

        item { HeroCard(ui) }

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

        if (ui.historyOpen) {
            item { LookupCard(ui, onChangeDate = { showPicker = true }) }
        }

        if (ui.future.isNotEmpty()) {
            item { FutureCard(ui) }
        }

        if (ui.indicators.isNotEmpty()) {
            item { IndicatorsCard(ui) }
        }

        if (ui.historyOpen) {
            item { SectionTitle("Detalle diario") }
            items(ui.chartValues.reversed().take(180), key = { it.date.toString() }) { v ->
                KeyValueRow(
                    label = Fmt.shortDate(v.date) +
                        if (v.date.isAfter(LocalDate.now())) "  ·  futuro publicado" else "",
                    value = Fmt.clpExact(v.value),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = ui.lookupDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        vm.lookup(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun HeroCard(ui: UfUiState) {
    AppCard {
        Text(
            ui.current?.let { Fmt.longDate(it.date) } ?: "Cargando…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(if (ui.historyOpen) "Histórico" else "Últimos 60 días")
            TextButton(onClick = vm::toggleHistory) {
                Text(if (ui.historyOpen) "Ocultar" else "Ver histórico")
            }
        }

        // The range chips sit above the chart, not below it: the control that
        // decides what the chart shows must be visible the moment history is
        // expanded, and a 200dp chart pushes anything under it off screen.
        AnimatedVisibility(visible = ui.historyOpen) {
            Column {
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
            }
        }

        // The scrubbed reading lives here, never in the hero: the headline must
        // keep telling the truth about what the UF is worth today.
        val scrubbed = ui.scrubbed
        Text(
            text = if (scrubbed != null)
                "${Fmt.shortDate(scrubbed.date)}   ${Fmt.clpExact(scrubbed.value)}"
            else if (ui.historyOpen)
                "Arrastra sobre el gráfico para explorar"
            else " ",
            style = MaterialTheme.typography.bodyMedium,
            color = if (scrubbed != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        Sparkline(
            values = ui.chartValues,
            height = if (ui.historyOpen) 200.dp else 120.dp,
            selectedIndex = ui.scrubIndex,
            onScrub = { if (ui.historyOpen) vm.scrub(it) },
        )

        AnimatedVisibility(visible = ui.historyOpen) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    ui.chartValues.firstOrNull()?.let { Fmt.shortDate(it.date) }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ui.rangeChangePct?.let { pct ->
                    Text(
                        "${Fmt.pctSigned(pct)} en el período",
                        style = MaterialTheme.typography.bodySmall,
                        color = signColor(pct),
                    )
                }
                Text(
                    ui.chartValues.lastOrNull()?.let { Fmt.shortDate(it.date) }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        Spacer(Modifier.height(8.dp))
        Text(
            ui.lookupValue?.let { Fmt.clpExact(it.value) } ?: "Sin dato",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        when {
            ui.lookupIsFuture -> Pill(
                text = "Valor oficial ya publicado",
                container = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            ui.lookupMissing && ui.lookupValue != null -> Text(
                "Sin dato exacto para esa fecha. Se muestra el " +
                    "${Fmt.shortDate(ui.lookupValue!!.date)}, el más cercano anterior.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ui.lookupValue == null -> Text(
                "Fuera de la cobertura disponible. La serie diaria de la UF parte en " +
                    "agosto de 1977.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
