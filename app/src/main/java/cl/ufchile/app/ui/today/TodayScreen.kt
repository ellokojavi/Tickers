package cl.ufchile.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import cl.ufchile.app.data.prefs.ThemeMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.domain.model.DataSource
import cl.ufchile.app.ui.appViewModel
import cl.ufchile.app.ui.components.AppCard
import cl.ufchile.app.ui.components.KeyValueRow
import cl.ufchile.app.ui.components.NumberField
import cl.ufchile.app.ui.components.Pill
import cl.ufchile.app.ui.components.SectionTitle
import cl.ufchile.app.ui.components.Sparkline
import cl.ufchile.app.ui.components.signColor
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun TodayScreen(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onCycleTheme: () -> Unit = {},
) {
    val vm = appViewModel { TodayViewModel(it.ufRepository, it.settings) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp
        ),
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

        if (ui.error != null) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        ui.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        item { ConverterCard(ui, vm) }

        if (ui.recent.size > 2) {
            item {
                AppCard {
                    SectionTitle("Últimos 60 días")
                    Sparkline(values = ui.recent, height = 120.dp)
                }
            }
        }

        if (ui.future.isNotEmpty()) {
            item { FutureCard(ui) }
        }

        if (ui.indicators.isNotEmpty()) {
            item { IndicatorsCard(ui) }
        }
    }
}

@Composable
private fun HeroCard(ui: TodayUiState) {
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
            color = MaterialTheme.colorScheme.onSurface,
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
                Pill(
                    text = "Actualizado ${Fmt.relativeDay(it.date)}",
                    icon = Icons.Outlined.Schedule,
                )
            }
        }
    }
}

@Composable
private fun ConverterCard(ui: TodayUiState, vm: TodayViewModel) {
    AppCard {
        SectionTitle("Conversor")
        NumberField(
            value = ui.ufText,
            onValueChange = vm::onUfInput,
            label = "UF",
            suffix = "UF",
        )
        Spacer(Modifier.height(10.dp))
        NumberField(
            value = ui.clpText,
            onValueChange = vm::onClpInput,
            label = "Pesos",
            suffix = "CLP",
        )
    }
}

@Composable
private fun FutureCard(ui: TodayUiState) {
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
            KeyValueRow(
                label = Fmt.shortDate(v.date),
                value = Fmt.clpExact(v.value),
            )
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
private fun IndicatorsCard(ui: TodayUiState) {
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
