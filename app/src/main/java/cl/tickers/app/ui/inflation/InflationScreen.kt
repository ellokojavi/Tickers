package cl.tickers.app.ui.inflation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
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
import cl.tickers.app.ui.appViewModel
import cl.tickers.app.ui.components.AppCard
import cl.tickers.app.ui.components.DateField
import cl.tickers.app.ui.components.KeyValueRow
import cl.tickers.app.ui.components.NumberField
import cl.tickers.app.ui.components.ScreenHeader
import cl.tickers.app.ui.components.SectionTitle
import java.time.LocalDate

@Composable
fun InflationScreen() {
    val vm = appViewModel { InflationViewModel(it.ufRepository) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "Calculadora de inflación") }

        item {
            AppCard {
                NumberField(
                    value = ui.amountText,
                    onValueChange = vm::setAmount,
                    label = "Monto en pesos",
                    suffix = "CLP",
                    allowDecimals = false,
                )
                Spacer(Modifier.height(16.dp))
                DateField(
                    label = "Desde",
                    value = ui.from,
                    onSelect = vm::setFrom,
                    min = ui.earliest,
                    max = ui.latest,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = vm::swap) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Invertir las fechas")
                    }
                }
                val today = LocalDate.now()
                DateField(
                    label = "Hasta",
                    value = ui.to,
                    onSelect = vm::setTo,
                    min = ui.earliest,
                    max = ui.latest,
                    // Coming back to today is the most common correction, and
                    // it should not cost a trip through the calendar.
                    quickLabel = if (ui.to != today) "Hoy" else null,
                    onQuick = { vm.setTo(today) },
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
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            "${Fmt.clp(r.amount)} del ${Fmt.longDate(r.from)} equivalen a",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { ShareReajuste.share(context, r) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Compartir la equivalencia",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Fmt.clp(r.adjustedAmount),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        "del ${Fmt.longDate(r.to)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    ui.substituted?.let { asked ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Sin dato exacto para el ${Fmt.shortDate(asked)}; se usó el día " +
                                "publicado más cercano anterior.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow("UF el ${Fmt.shortDate(r.from)}", Fmt.clpExact(r.ufAtFrom))
                    KeyValueRow("Equivale a", Fmt.uf4(r.ufUnits))
                    KeyValueRow("UF el ${Fmt.shortDate(r.to)}", Fmt.clpExact(r.ufAtTo))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    KeyValueRow("Reajuste", Fmt.factor(r.factor))
                    KeyValueRow("Variación acumulada", Fmt.pct(r.variationPct))
                    KeyValueRow("Equivalente anual", Fmt.pct(r.annualisedPct))
                    KeyValueRow("Período", Fmt.period(r.days))
                }
            }
        }

        item {
            AppCard {
                SectionTitle("Cómo se calcula")
                Text(
                    "El monto se convierte a UF en la fecha inicial y se vuelve a pesos en la " +
                        "fecha final. Es el mismo mecanismo con que se reajustan contratos, " +
                        "arriendos y deudas en Chile, y como la UF se publica todos los días, " +
                        "el cálculo es exacto al día.\n\n" +
                        "La UF de un día se reajusta con el IPC del mes anterior, así que " +
                        "refleja la inflación con ese desfase.\n\n" +
                        "Cobertura: ${Fmt.shortDate(ui.earliest)} a ${Fmt.shortDate(ui.latest)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

