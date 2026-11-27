package com.myra.assistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
fun MyraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MyraDarkColorScheme,
        typography = MyraTypography,
        shapes = MyraShapes,
        content = content
    )
}
