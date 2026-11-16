package io.github.jfloresriera.reto10.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Temas disponibles; el id se guarda en las preferencias. */
enum class AppTheme(val id: String, val label: String) {
    GREEN("green", "Verde campo"),
    PURPLE("purple", "Morado");

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: GREEN
    }
}

/** Colores de marca que cambian con el tema (misma estructura que GulupaScan). */
@Immutable
data class BrandColors(
    val dark: Color,
    val main: Color,
    val soft: Color,
    val background: Color,
    val card: Color,
    val border: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textSoft: Color,
    val ramp: List<Color>, // rampa secuencial del mapa, de claro a oscuro
)

val GreenBrand = BrandColors(
    dark = Color(0xFF005828),
    main = Color(0xFF005828),
    soft = Color(0xFFE1F2E7),
    background = Color(0xFFF7F7F7),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE4E2E3),
    surfaceVariant = Color(0xFFEFEDEE),
    text = Color(0xFF2A1A22),
    textSoft = Color(0xFF6A5E64),
    ramp = listOf(
        Color(0xFFE8F5E9), Color(0xFFC8E6C9), Color(0xFF81C784),
        Color(0xFF43A047), Color(0xFF2E7D32), Color(0xFF1B5E20),
    ),
)

val PurpleBrand = BrandColors(
    dark = Color(0xFF9034BD),
    main = Color(0xFF5D2741),
    soft = Color(0xFFF3E6F9),
    background = Color(0xFFF7F7F7),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFEADBF1),
    surfaceVariant = Color(0xFFF2EAF5),
    text = Color(0xFF2A1A22),
    textSoft = Color(0xFF6E6178),
    ramp = listOf(
        Color(0xFFF3E6F9), Color(0xFFE0C3EF), Color(0xFFC48FDE),
        Color(0xFF9034BD), Color(0xFF6D2A94), Color(0xFF4A1A63),
    ),
)

val LocalBrand = staticCompositionLocalOf { GreenBrand }

val BrandDark: Color @Composable @ReadOnlyComposable get() = LocalBrand.current.dark
val Brand: Color @Composable @ReadOnlyComposable get() = LocalBrand.current.main
val BrandSoft: Color @Composable @ReadOnlyComposable get() = LocalBrand.current.soft
val AppBg: Color @Composable @ReadOnlyComposable get() = LocalBrand.current.background
val BorderColor: Color @Composable @ReadOnlyComposable get() = LocalBrand.current.border
val TextMain: Color @Composable @ReadOnlyComposable get() = LocalBrand.current.text
val TextSoft: Color @Composable @ReadOnlyComposable get() = LocalBrand.current.textSoft

val ErrorRed = Color(0xFFE53935)

fun brandOf(theme: AppTheme) = if (theme == AppTheme.PURPLE) PurpleBrand else GreenBrand

private fun scheme(b: BrandColors) = lightColorScheme(
    primary = b.dark,
    onPrimary = Color.White,
    primaryContainer = b.soft,
    onPrimaryContainer = b.dark,
    secondary = b.main,
    onSecondary = Color.White,
    background = b.background,
    onBackground = b.text,
    surface = b.card,
    onSurface = b.text,
    surfaceVariant = b.surfaceVariant,
    onSurfaceVariant = b.textSoft,
    outline = b.border,
    error = ErrorRed,
)

private fun typography(b: BrandColors) = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, color = b.main),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

@Composable
fun EvaTheme(theme: AppTheme = AppTheme.GREEN, content: @Composable () -> Unit) {
    val brand = brandOf(theme)
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme(brand), typography = typography(brand), content = content)
    }
}
