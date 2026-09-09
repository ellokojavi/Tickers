package cl.tickers.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.tickers.app.core.format.Fmt
import cl.tickers.app.domain.engine.UfEngine
import cl.tickers.app.domain.model.HasValue

/**
 * The one "Histórico" card. Every series in the app (UF, dollar, bitcoin) is
 * drawn through this so the charts behave the same way without anyone having
 * to remember to make them:
 *
 *  - a row of range chips, then one line of text, then the line, then both
 *    ends labelled with their moment and value;
 *  - the text line carries the window's change and its annualised rate, or
 *    the point under the finger while scrubbing, or why there is no chart;
 *  - scrubbing changes nothing outside this card: the hero above it keeps
 *    showing today's value, because that is what the screen is for.
 *
 * The screen decides what a point's moment and amount look like ([stamp],
 * [amount]) and where the data comes from; this decides everything else.
 */
@Composable
fun <R, P : HasValue> HistoryChartCard(
    ranges: List<R>,
    range: R,
    onRange: (R) -> Unit,
    rangeLabel: (R) -> String,
    points: List<P>,
    stamp: (P) -> String,
    amount: (P) -> String,
    summary: UfEngine.ChartSummary?,
    scrubIndex: Int?,
    onScrub: (Int?) -> Unit,
    loading: Boolean = false,
    message: String? = null,
) {
    AppCard {
        SectionTitle("Histórico")

        ChipRow(
            options = ranges,
            selected = range,
            onSelect = onRange,
            label = rangeLabel,
        )
        if (loading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // One line above the chart carries either the period summary or, while
        // a finger is down, the point being explored. Below the chart it would
        // fall past the fold.
        val scrubbed = scrubIndex?.let { points.getOrNull(it) }
        when {
            scrubbed != null -> Text(
                "${stamp(scrubbed)}   ${amount(scrubbed)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            points.size < 2 -> Text(
                if (loading) "Cargando el gráfico…" else "Sin datos para este rango",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            summary != null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${Fmt.pctSigned(summary.periodPct)} en el período",
                    style = MaterialTheme.typography.bodyMedium,
                    color = signColor(summary.periodPct),
                )
                summary.annualisedPct?.let { annual ->
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
            }

            // Keeps the chart from jumping when the line appears.
            else -> Text(" ", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))

        Sparkline(
            values = points,
            height = CHART_HEIGHT,
            selectedIndex = scrubIndex,
            onScrub = onScrub,
        )

        // Both ends are labelled with their value, so the chart can be read
        // without touching it.
        if (points.size >= 2) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EndpointLabel(stamp(points.first()), amount(points.first()), alignEnd = false)
                EndpointLabel(stamp(points.last()), amount(points.last()), alignEnd = true)
            }
        }
    }
}

/** The one chart height. The web draws its charts at the same 200 px. */
private val CHART_HEIGHT = 200.dp

@Composable
private fun EndpointLabel(stamp: String, amount: String, alignEnd: Boolean) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            stamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
