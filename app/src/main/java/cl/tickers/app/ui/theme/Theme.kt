package cl.tickers.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import cl.tickers.app.data.prefs.ThemeMode

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The theme the system would give us, for as long as the user has not chosen one. */
@Composable
fun systemThemeMode(): ThemeMode =
    if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT

@Composable
fun TickersTheme(
    themeMode: ThemeMode = systemThemeMode(),
    content: @Composable () -> Unit,
) {
    val dark = themeMode == ThemeMode.DARK

    CompositionLocalProvider(
        LocalSignColors provides if (dark) DarkSignColors else LightSignColors
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
