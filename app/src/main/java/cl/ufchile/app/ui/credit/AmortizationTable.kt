package cl.ufchile.app.ui.credit

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.ufchile.app.core.format.Fmt
import cl.ufchile.app.domain.model.AmortizationRow
import cl.ufchile.app.domain.model.MortgageResult
import java.math.BigDecimal
import java.math.RoundingMode

private data class Col(val title: String, val width: Int)

private val COLUMNS = listOf(
    Col("N°", 44),
    Col("Fecha", 88),
    Col("Saldo inicial", 104),
    Col("Interés", 88),
    Col("Amortiza", 88),
    Col("Dividendo", 96),
    Col("Desgrav.", 80),
    Col("Incendio", 80),
    Col("Prepago", 88),
    Col("Total mes", 104),
    Col("Saldo final", 104),
)

/**
 * The full payment table. Rows share a single horizontal scroll state so the
 * header and every row stay aligned while the user pans sideways.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmortizationTableDialog(
    title: String,
    result: MortgageResult,
    ufValue: BigDecimal?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Tabla de pagos", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Fmt.integer(result.schedule.size)} cuotas · valores en UF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            ScheduleExport.share(
                                context = context,
                                fileName = "${title.ifBlank { "simulacion" }}.csv",
                                csv = ScheduleExport.buildCsv(title, result, ufValue),
                            )
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Exportar CSV")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .horizontalScroll(scroll)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COLUMNS.forEach { col ->
                        Text(
                            col.title,
                            modifier = Modifier.width(col.width.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = if (col.title == "Fecha" || col.title == "N°")
                                TextAlign.Start else TextAlign.End,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(Modifier.fillMaxSize()) {
                    items(result.schedule, key = { it.number }) { row ->
                        ScheduleRow(row, scroll)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    row: AmortizationRow,
    scroll: androidx.compose.foundation.ScrollState,
) {
    val cells = remember(row) {
        listOf(
            Fmt.integer(row.number),
            Fmt.shortDate(row.date),
            n(row.openingBalanceUf),
            n(row.interestUf),
            n(row.principalUf),
            n(row.paymentUf),
            n(row.lifeInsuranceUf),
            n(row.fireInsuranceUf),
            if (row.prepaymentUf.signum() > 0) n(row.prepaymentUf) else "—",
            n(row.totalOutflowUf),
            n(row.closingBalanceUf),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                modifier = Modifier.width(COLUMNS[i].width.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = if (i <= 1) TextAlign.Start else TextAlign.End,
                color = if (i == 0) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Two decimals is what a payment table needs; four is noise on a phone. */
private fun n(v: BigDecimal): String =
    Fmt.uf(v.setScale(2, RoundingMode.HALF_UP)).removeSuffix(" UF")
