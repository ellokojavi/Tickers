package cl.ufchile.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Three destinations, one per question the app answers: what the UF is worth,
 * what money from another era is worth, and what a mortgage costs.
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
    INFLATION("inflation", "Inflación", Icons.Outlined.TrendingUp),
    CREDITS("credits", "Créditos", Icons.Outlined.AccountBalance),
}

object Routes {
    const val CREDIT_EDITOR = "credits/editor"
    fun creditEditor(id: Long) = "$CREDIT_EDITOR/$id"
}
