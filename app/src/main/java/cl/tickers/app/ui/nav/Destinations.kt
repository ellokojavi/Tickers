package cl.tickers.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Two sections: the market figures the app reports, then the calculators it
 * started as. The bar draws a hairline where the section changes.
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
    val section: Section,
) {
    VALUE("uf", "UF", Icons.Outlined.ShowChart, Section.INDICATORS),

    /** Published once a business day, like the UF, and read the same way. */
    DOLLAR("usd", "Dólar", Icons.Outlined.AttachMoney, Section.INDICATORS),

    /**
     * Last of the indicators because it is the one figure here that is a
     * market rather than a published rate: it moves by the second, has an
     * intraday chart, and comes from exchanges rather than the Banco Central.
     */
    BITCOIN("btc", "Bitcoin", Icons.Outlined.CurrencyBitcoin, Section.INDICATORS),

    // A calculator, not a trend line: the screen is a calculator, and the
    // rising-arrow icon was near-indistinguishable from the chart icon next
    // to it in the bar.
    INFLATION("inflation", "Inflación", Icons.Outlined.Calculate, Section.TOOLS),
    CREDITS("credits", "Créditos", Icons.Outlined.AccountBalance, Section.TOOLS);

    enum class Section { INDICATORS, TOOLS }
}

object Routes {
    const val CREDIT_EDITOR = "credits/editor"
    fun creditEditor(id: Long) = "$CREDIT_EDITOR/$id"
}
