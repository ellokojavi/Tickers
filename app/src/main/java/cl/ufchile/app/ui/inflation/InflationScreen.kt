package cl.ufchile.app.ui.inflation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import cl.ufchile.app.ui.components.KeyValueRow
import cl.ufchile.app.ui.components.NumberField
import cl.ufchile.app.ui.components.ScreenHeader
import cl.ufchile.app.ui.components.SectionTitle
import java.time.YearMonth

@Composable
fun InflationScreen() {
    val vm = appViewModel { InflationViewModel(it.ufRepository) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(title = "Calculadora de inflación")
        }

        item {
            AppCard {
                NumberField(
                    value = ui.amountText,
                    onValueChange = vm::setAmount,
                    label = "Monto en pesos",
                    suffix = "CLP",
                )
                Spacer(Modifier.height(14.dp))
                // Stacked rather than side by side: two month+year pickers do
                // not fit across a phone without the labels wrapping.
                MonthSelector(
                    label = "Desde",
                    value = ui.from,
                    earliest = ui.earliest,
                    latest = ui.latest,
                    onSelect = vm::setFrom,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = vm::swap) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Invertir períodos")
                    }
                }
                MonthSelector(
                    label = "Hasta",
                    value = ui.to,
                    earliest = ui.earliest,
                    latest = ui.latest,
                    onSelect = vm::setTo,
                )
            }
        }

        ui.error?.let { message ->
            item {
                AppCard {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        ui.result?.let { r ->
            item {
                AppCard {
                    SectionTitle("Según el IPC")
                    Text(
                        "${Fmt.clp(r.amount)} de ${Fmt.monthYear(r.from)} equivalen a",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Fmt.clp(r.adjustedAmount),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        "de ${Fmt.monthYear(r.to)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow("Factor de ajuste", Fmt.factor(r.factor))
                    KeyValueRow("Inflación acumulada", Fmt.pct(r.cumulativePct))
                    KeyValueRow("Equivalente anual", Fmt.pct(r.annualizedPct))
                    KeyValueRow("Período", "${r.months} meses")
                }
            }
        }

        ui.ufEquivalence?.let { uf ->
            item {
                AppCard {
                    SectionTitle("Según la UF")
                    KeyValueRow("UF en ${Fmt.monthYearShort(ui.from)}", Fmt.clpExact(uf.ufValueAtOrigin))
                    KeyValueRow("Equivale a", Fmt.uf4(uf.ufUnits))
                    KeyValueRow("UF en ${Fmt.monthYearShort(ui.to)}", Fmt.clpExact(uf.ufValueAtTarget))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow(
                        "Monto ajustado",
                        Fmt.clp(uf.adjustedAmount),
                        emphasise = true,
                    )
                    Text(
                        "Da distinto al cálculo por IPC porque la UF de un mes reajusta " +
                            "según el IPC de dos meses antes. Esta cifra responde a: si ese " +
                            "monto se hubiera guardado en UF, cuánto valdría hoy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Cómo se calcula")
                Text(
                    "El índice de precios se deriva de la propia UF: su valor al día 9 de cada mes " +
                        "varía exactamente según el IPC del mes correspondiente. Eso evita encadenar " +
                        "variaciones publicadas con un decimal y mantiene la fuente oficial.\n\n" +
                        "Cobertura: ${Fmt.monthYear(ui.earliest)} a ${Fmt.monthYear(ui.latest)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MonthSelector(
    label: String,
    value: YearMonth,
    earliest: YearMonth,
    latest: YearMonth,
    onSelect: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var yearOpen by remember { mutableStateOf(false) }
    var monthOpen by remember { mutableStateOf(false) }

    val years = (earliest.year..latest.year).toList()
    val months = (1..12).map { YearMonth.of(value.year, it) }
        .filter { !it.isBefore(earliest) && !it.isAfter(latest) }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { monthOpen = true },
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(Fmt.monthYearShort(value).substringBefore(" "), maxLines = 1, softWrap = false)
            }
            DropdownMenu(expanded = monthOpen, onDismissRequest = { monthOpen = false }) {
                months.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(Fmt.monthYear(m).substringBefore(" ")) },
                        onClick = { onSelect(m); monthOpen = false },
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { yearOpen = true },
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${value.year}", maxLines = 1, softWrap = false)
            }
            DropdownMenu(expanded = yearOpen, onDismissRequest = { yearOpen = false }) {
                years.reversed().forEach { y ->
                    DropdownMenuItem(
                        text = { Text("$y") },
                        onClick = {
                            val candidate = YearMonth.of(y, value.monthValue)
                            onSelect(
                                when {
                                    candidate.isBefore(earliest) -> earliest
                                    candidate.isAfter(latest) -> latest
                                    else -> candidate
                                }
                            )
                            yearOpen = false
                        },
                    )
                }
            }
        }
    }
}
