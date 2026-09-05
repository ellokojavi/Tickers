package cl.ufchile.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TODAY("today", "Hoy", Icons.Outlined.ShowChart),
    HISTORY("history", "Histórico", Icons.Outlined.Timeline),
    INFLATION("inflation", "Inflación", Icons.Outlined.TrendingUp),
    CREDITS("credits", "Créditos", Icons.Outlined.AccountBalance),
}

object Routes {
    const val CREDIT_EDITOR = "credits/editor"
    fun creditEditor(id: Long) = "$CREDIT_EDITOR/$id"
}
