package com.myra.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myra.assistant.R

object MyraColors {
    val BgObsidian = Color(0xFF050508)
    val BgDeep = Color(0xFF0A0A12)
    val SurfaceCard = Color(0xFF11111A)
    val SurfaceGlass = Color(0xCC12121D)
    val SurfaceElevated = Color(0xFF1A1A28)
    val BorderSubtle = Color(0x1FFFFFFF)
    val BorderAccent = Color(0x4DFF1744)

    val Crimson = Color(0xFFFF1744)
    val RoseGlow = Color(0xFFFF4081)
    val Violet = Color(0xFFD500F9)
    val Cyan = Color(0xFF40C4FF)
    val Emerald = Color(0xFF00E676)
    val Amber = Color(0xFFFFAB40)

    val TextPrimary = Color(0xFFF5F5FA)
    val TextSecondary = Color(0xFF9B9BB0)
    val TextMuted = Color(0xFF6B6B80)
}

@Immutable
data class MyraUiPalette(
    val isDark: Boolean,
    val background: Color,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val surfaceCard: Color,
    val surfaceAlt: Color,
    val surfaceHighlight: Color,
    val dockBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val accent: Color = MyraColors.Crimson,
    val pinkGlow: Color = MyraColors.RoseGlow
)

private val DarkPalette = MyraUiPalette(
    isDark = true,
    background = Color(0xFF06060B),
    backgroundTop = Color(0xFF0B0A11),
    backgroundBottom = Color(0xFF08070D),
    surfaceCard = Color(0xFF11111A),
    surfaceAlt = Color(0xFF171723),
    surfaceHighlight = Color(0xFF1A0D16),
    dockBackground = Color(0xFF0B0B12),
    textPrimary = Color(0xFFF5F5FA),
    textSecondary = Color(0xFF9E9EB4),
    textMuted = Color(0xFF727288),
    borderSubtle = Color.White.copy(alpha = 0.08f)
)

private val LightPalette = MyraUiPalette(
    isDark = false,
    background = Color(0xFFF8F7FB),
    backgroundTop = Color(0xFFFFFFFF),
    backgroundBottom = Color(0xFFF2EEF6),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1EFF7),
    surfaceHighlight = Color(0xFFFFEDF2),
    dockBackground = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF12121A),
    textSecondary = Color(0xFF56566A),
    textMuted = Color(0xFF7D7D94),
    borderSubtle = Color(0xFF12121A).copy(alpha = 0.09f)
)

val LocalMyraPalette = staticCompositionLocalOf { DarkPalette }

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

private val MyraDarkColorScheme = darkColorScheme(
    primary = MyraColors.Crimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A081A),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = MyraColors.Violet,
    onSecondary = Color.White,
    tertiary = MyraColors.Cyan,
    onTertiary = Color(0xFF04131A),
    background = MyraColors.BgObsidian,
    onBackground = MyraColors.TextPrimary,
    surface = MyraColors.SurfaceCard,
    onSurface = MyraColors.TextPrimary,
    surfaceVariant = MyraColors.SurfaceElevated,
    onSurfaceVariant = MyraColors.TextSecondary,
    outline = MyraColors.BorderSubtle
)

private val MyraLightColorScheme = lightColorScheme(
    primary = MyraColors.Crimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = Color(0xFF4A081A),
    secondary = MyraColors.Violet,
    onSecondary = Color.White,
    tertiary = MyraColors.Cyan,
    onTertiary = Color.White,
    background = LightPalette.background,
    onBackground = LightPalette.textPrimary,
    surface = LightPalette.surfaceCard,
    onSurface = LightPalette.textPrimary,
    surfaceVariant = LightPalette.surfaceAlt,
    onSurfaceVariant = LightPalette.textSecondary,
    outline = LightPalette.borderSubtle
)

val MyraTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 4.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 1.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.3.sp
    )
)

val MyraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun MyraTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }
    val palette = if (useDark) DarkPalette else LightPalette
    val colorScheme = if (useDark) MyraDarkColorScheme else MyraLightColorScheme

    CompositionLocalProvider(LocalMyraPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MyraTypography,
            shapes = MyraShapes,
            content = content
        )
    }
}
