package cl.tickers.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The three calculators the app started as, then the two market prices.
 *
 * Five is the most a bottom bar can hold and still be tapped accurately, so
 * this is the ceiling: anything further has to go inside one of these rather
 * than beside them. The labels share the width evenly for the same reason.
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
    VALUE("uf", "UF", Icons.Outlined.ShowChart),
    // A calculator, not a trend line: the screen is a calculator, and the
    // rising-arrow icon was near-indistinguishable from the chart icon next
    // to it in the bar.
    INFLATION("inflation", "Inflación", Icons.Outlined.Calculate),
    CREDITS("credits", "Créditos", Icons.Outlined.AccountBalance),

    /** Published once a business day, like the UF, and read the same way. */
    DOLLAR("usd", "Dólar", Icons.Outlined.AttachMoney),

    /**
     * Last because it is the one figure here that is a market rather than a
     * published rate: it moves by the second, has an intraday chart, and comes
     * from exchanges rather than the Banco Central.
     */
    BITCOIN("btc", "Bitcoin", Icons.Outlined.CurrencyBitcoin),
}

object Routes {
    const val CREDIT_EDITOR = "credits/editor"
    fun creditEditor(id: Long) = "$CREDIT_EDITOR/$id"
}
