package bd.robin.hisabkhata.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import bd.robin.hisabkhata.R

val Leaf = Color(0xFF0E7A45)
val LeafDark = Color(0xFF075C32)
val Ink = Color(0xFF1B2430)
val Paper = Color(0xFFFFF8F1)
val Amber = Color(0xFFE39B12)
val Clay = Color(0xFFC2412D)

data class ExtraColors(
    val get: Color,
    val give: Color,
    val amber: Color,
    val softGet: Color,
    val softGive: Color,
    val softAmber: Color,
    val tile: Color,
)

val LocalExtra = staticCompositionLocalOf {
    ExtraColors(Leaf, Clay, Amber, Color(0xFFE5F6EC), Color(0xFFFFE8E2), Color(0xFFFFF3D6), Color.White)
}

private val Bengali = FontFamily(
    Font(R.font.noto_sans_bengali_regular, FontWeight.Normal),
    Font(R.font.noto_sans_bengali_regular, FontWeight.Medium),
    Font(R.font.noto_sans_bengali_bold, FontWeight.SemiBold),
    Font(R.font.noto_sans_bengali_bold, FontWeight.Bold),
)

private fun type(): Typography {
    val base = Typography()
    fun TextStyle.bangla(size: androidx.compose.ui.unit.TextUnit = fontSize, weight: FontWeight = fontWeight ?: FontWeight.Normal) =
        copy(fontFamily = Bengali, fontSize = size, fontWeight = weight)
    return base.copy(
        displayLarge = base.displayLarge.bangla(weight = FontWeight.Bold),
        displayMedium = base.displayMedium.bangla(weight = FontWeight.Bold),
        displaySmall = base.displaySmall.bangla(weight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.bangla(34.sp, FontWeight.Bold),
        headlineMedium = base.headlineMedium.bangla(28.sp, FontWeight.Bold),
        headlineSmall = base.headlineSmall.bangla(22.sp, FontWeight.Bold),
        titleLarge = base.titleLarge.bangla(20.sp, FontWeight.Bold),
        titleMedium = base.titleMedium.bangla(17.sp, FontWeight.SemiBold),
        titleSmall = base.titleSmall.bangla(15.sp, FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.bangla(17.sp),
        bodyMedium = base.bodyMedium.bangla(15.sp),
        bodySmall = base.bodySmall.bangla(13.sp),
        labelLarge = base.labelLarge.bangla(15.sp, FontWeight.SemiBold),
        labelMedium = base.labelMedium.bangla(13.sp, FontWeight.Medium),
        labelSmall = base.labelSmall.bangla(11.sp),
    )
}

@Composable
fun HisabTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF7DDBA4),
            onPrimary = Color(0xFF00391C),
            secondary = Color(0xFFFFC56B),
            onSecondary = Color(0xFF3F2E00),
            tertiary = Color(0xFFFFB4A4),
            background = Color(0xFF121613),
            surface = Color(0xFF1C2420),
            surfaceVariant = Color(0xFF2A3330),
        )
    } else {
        lightColorScheme(
            primary = Leaf,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD8F3E4),
            onPrimaryContainer = LeafDark,
            secondary = Amber,
            onSecondary = Color(0xFF2B2108),
            secondaryContainer = Color(0xFFFFF3D6),
            tertiary = Clay,
            onTertiary = Color.White,
            background = Paper,
            surface = Color.White,
            surfaceVariant = Color(0xFFF6EFE6),
            onSurface = Ink,
            onSurfaceVariant = Color(0xFF5C6B7A),
            outline = Color(0xFFE4D8CC),
        )
    }
    val extra = if (dark) {
        ExtraColors(
            get = Color(0xFF7DDBA4),
            give = Color(0xFFFFB4A4),
            amber = Color(0xFFFFC56B),
            softGet = Color(0xFF143524),
            softGive = Color(0xFF3A201C),
            softAmber = Color(0xFF3A2E14),
            tile = Color(0xFF24302B),
        )
    } else {
        ExtraColors(Leaf, Clay, Amber, Color(0xFFE5F6EC), Color(0xFFFFE8E2), Color(0xFFFFF3D6), Color.White)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = type(), content = content)
    }
}
