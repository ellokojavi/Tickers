package cl.tickers.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Four destinations, one per question the app answers: what the UF is worth,
 * what a bitcoin is worth, what money from another era is worth, and what a
 * mortgage costs.
 *
 * Today's value and the historical series used to be separate tabs. They are
 * the same question at different points in time, so they were merged into one
 * screen whose history controls expand on demand.
 */
enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    VALUE("uf", "Valor UF", Icons.Outlined.ShowChart),

    /**
     * Next to the UF because both answer "what is this worth today", and on its
     * own tab rather than inside that screen because its content is a different
     * shape: a price that moves by the second, an intraday chart, and a market
     * rather than a published figure. When the dollar arrives it will join the
     * UF tab instead, since those two are the same shape.
     */
    BITCOIN("btc", "Bitcoin", Icons.Outlined.CurrencyBitcoin),
    // A calculator, not a trend line: the screen is a calculator, and the
    // rising-arrow icon was near-indistinguishable from the chart icon next
    // to it in the bar.
    INFLATION("inflation", "Inflación", Icons.Outlined.Calculate),
    CREDITS("credits", "Créditos", Icons.Outlined.AccountBalance),
}

object Routes {
    const val CREDIT_EDITOR = "credits/editor"
    fun creditEditor(id: Long) = "$CREDIT_EDITOR/$id"
}
