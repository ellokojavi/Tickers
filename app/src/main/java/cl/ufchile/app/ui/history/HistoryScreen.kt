package cl.ufchile.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import cl.ufchile.app.ui.appViewModel
import cl.ufchile.app.ui.components.AppCard
import cl.ufchile.app.ui.components.ChipRow
import cl.ufchile.app.ui.components.KeyValueRow
import cl.ufchile.app.ui.components.Pill
import cl.ufchile.app.ui.components.SectionTitle
import cl.ufchile.app.ui.components.Sparkline
import cl.ufchile.app.ui.components.signColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val vm = appViewModel { HistoryViewModel(it.ufRepository) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Histórico",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        item {
            AppCard {
                val shown = ui.selected ?: ui.last
                Text(
                    shown?.let { Fmt.longDate(it.date) } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    shown?.let { Fmt.clpExact(it.value) } ?: "—",
                    style = MaterialTheme.typography.displayMedium,
                )
                ui.changePct?.let { pct ->
                    Text(
                        "${Fmt.pctSigned(pct)} en el período",
                        style = MaterialTheme.typography.bodyMedium,
                        color = signColor(pct),
                    )
                }
                // The series legitimately runs past today, so the headline can
                // land on a future day. Say so instead of letting it pass as
                // the current value.
                if (shown != null && shown.date.isAfter(LocalDate.now())) {
                    Spacer(Modifier.height(8.dp))
                    Pill(
                        text = "Valor oficial ya publicado",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Sparkline(
                    values = ui.visible,
                    height = 200.dp,
                    selectedIndex = ui.scrubIndex,
                    onScrub = vm::scrub,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        ui.first?.let { Fmt.shortDate(it.date) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        ui.last?.let { Fmt.shortDate(it.date) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    options = Range.entries.toList(),
                    selected = ui.range,
                    onSelect = vm::setRange,
                    label = { it.label },
                )
                if (ui.loadingOlder) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                ui.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Consultar una fecha")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fmt.longDate(ui.lookupDate), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { showPicker = true }) { Text("Cambiar") }
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
                        "Sin dato exacto para esa fecha. Se muestra el ${Fmt.shortDate(ui.lookupValue!!.date)}, " +
                            "el más cercano anterior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ui.lookupValue == null -> Text(
                        "Fuera de la cobertura disponible. La serie diaria de la UF parte en agosto de 1977.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionTitle("Detalle diario") }

        items(ui.visible.reversed().take(180), key = { it.date.toString() }) { v ->
            KeyValueRow(
                label = Fmt.shortDate(v.date) +
                    if (v.date.isAfter(LocalDate.now())) "  ·  futuro publicado" else "",
                value = Fmt.clpExact(v.value),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        vm.lookup(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
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
