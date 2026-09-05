package cl.ufchile.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// A single deep-teal accent over cool neutrals. One accent keeps the numbers
// the loudest thing on screen; green and red are reserved exclusively for the
// sign of a variation so they never read as decoration.

private val Teal10 = Color(0xFF00201F)
private val Teal20 = Color(0xFF003734)
private val Teal30 = Color(0xFF00504C)
private val Teal40 = Color(0xFF0D5C63)
private val Teal80 = Color(0xFF7FD3CE)
private val Teal90 = Color(0xFFC8E9E6)

val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = Color.White,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    secondary = Color(0xFF4A6360),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E4),
    onSecondaryContainer = Color(0xFF05201E),
    background = Color(0xFFF7F9F9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFECF1F0),
    onSurfaceVariant = Color(0xFF4A5453),
    // Dialogs and menus pull from these. Left undefined they fall back to the
    // Material baseline, which is lavender and fights the palette.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F8F8),
    surfaceContainer = Color(0xFFF1F5F4),
    surfaceContainerHigh = Color(0xFFEBF0EF),
    surfaceContainerHighest = Color(0xFFE5EBEA),
    surfaceTint = Teal40,
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFEFF1F0),
    outline = Color(0xFF7A8483),
    outlineVariant = Color(0xFFDDE4E3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal90,
    secondary = Color(0xFFB0CCC8),
    onSecondary = Color(0xFF1B3532),
    secondaryContainer = Color(0xFF324B48),
    onSecondaryContainer = Color(0xFFCCE8E4),
    background = Color(0xFF0E1413),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF141B1A),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF1E2726),
    onSurfaceVariant = Color(0xFFBEC9C7),
    surfaceContainerLowest = Color(0xFF090E0E),
    surfaceContainerLow = Color(0xFF141B1A),
    surfaceContainer = Color(0xFF181F1E),
    surfaceContainerHigh = Color(0xFF222A29),
    surfaceContainerHighest = Color(0xFF2D3534),
    surfaceTint = Teal80,
    inverseSurface = Color(0xFFDDE4E2),
    inverseOnSurface = Color(0xFF2B3231),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF2C3634),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Semantic colours that Material's scheme has no slot for. */
@Immutable
data class SignColors(
    val positive: Color,
    val negative: Color,
    val neutral: Color,
    val future: Color,
    /**
     * Copihue crimson. Decorative only — never used to encode a value, so it
     * cannot be mistaken for the negative sign colour.
     */
    val copihue: Color,
)

val LightSignColors = SignColors(
    positive = Color(0xFF176B3A),
    negative = Color(0xFFB3261E),
    neutral = Color(0xFF6F7977),
    future = Color(0xFF5B4B8A),
    copihue = Color(0xFFC8384F),
)

val DarkSignColors = SignColors(
    positive = Color(0xFF6FD69A),
    negative = Color(0xFFFF9A90),
    neutral = Color(0xFF8B9694),
    future = Color(0xFFC0B0F0),
    copihue = Color(0xFFE7607A),
)

val LocalSignColors = staticCompositionLocalOf { LightSignColors }
