package bd.robin.hisabkhata.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bd.robin.hisabkhata.R

val NsBrand = Color(0xFF0F6B56)
val NsInk = Color(0xFF08261F)
val NsPaper = Color(0xFFF3F6F4)
val Leaf = NsBrand
val LeafDark = NsInk
val Ink = Color(0xFF14221C)
val Paper = NsPaper
val Amber = Color(0xFFB7791F)
val Clay = Color(0xFF9E3B32)

data class ExtraColors(
    val get: Color,
    val give: Color,
    val amber: Color,
    val softGet: Color,
    val softGive: Color,
    val softAmber: Color,
    val tile: Color,
)

/** বাকি / বিক্রি — money given on credit. */
const val DefaultSellHex = "C62828"

/** জমা / আদায় — money received. */
const val DefaultRecoveryHex = "1B7F4A"

val SellShades = listOf("C62828", "B71C1C", "E65100", "AD1457", "6A1B9A")
val RecoveryShades = listOf("1B7F4A", "2E7D32", "00695C", "1565C0", "00838F")
const val BlindSellHex = "D55E00"
const val BlindRecoveryHex = "0072B2"

fun colorFromHex(hex: String?, fallback: String = DefaultSellHex): Color {
    val clean = hex?.trim()?.removePrefix("#").orEmpty().ifBlank { fallback }
    val value = clean.toLongOrNull(16) ?: fallback.toLong(16)
    return Color(0xFF000000 or value)
}

private fun Color.soften(dark: Boolean): Color {
    val base = if (dark) Color(0xFF14211C) else Color.White
    val amount = if (dark) 0.28f else 0.16f
    return Color(
        red = base.red * (1f - amount) + red * amount,
        green = base.green * (1f - amount) + green * amount,
        blue = base.blue * (1f - amount) + blue * amount,
    )
}

val LocalExtra = staticCompositionLocalOf {
    ExtraColors(NsBrand, Clay, Amber, Color(0xFFE5F3EE), Color(0xFFF8E8E6), Color(0xFFF8F1E4), Color.White)
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
        copy(fontFamily = Bengali, fontSize = size, fontWeight = weight, lineHeight = size * 1.25)
    return base.copy(
        displayLarge = base.displayLarge.bangla(weight = FontWeight.Bold),
        displayMedium = base.displayMedium.bangla(weight = FontWeight.Bold),
        displaySmall = base.displaySmall.bangla(weight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.bangla(34.sp, FontWeight.Bold),
        headlineMedium = base.headlineMedium.bangla(28.sp, FontWeight.Bold),
        headlineSmall = base.headlineSmall.bangla(22.sp, FontWeight.Bold),
        titleLarge = base.titleLarge.bangla(20.sp, FontWeight.Bold),
        titleMedium = base.titleMedium.bangla(16.sp, FontWeight.SemiBold),
        titleSmall = base.titleSmall.bangla(14.sp, FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.bangla(16.sp),
        bodyMedium = base.bodyMedium.bangla(14.sp),
        bodySmall = base.bodySmall.bangla(12.sp),
        labelLarge = base.labelLarge.bangla(14.sp, FontWeight.SemiBold),
        labelMedium = base.labelMedium.bangla(12.sp, FontWeight.Medium),
        labelSmall = base.labelSmall.bangla(11.sp),
    )
}

private val NsShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun HisabTheme(
    dark: Boolean = isSystemInDarkTheme(),
    sell: Color = colorFromHex(DefaultSellHex),
    recovery: Color = colorFromHex(DefaultRecoveryHex),
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8ED4BE),
            onPrimary = Color(0xFF00382A),
            primaryContainer = Color(0xFF0E3D32),
            onPrimaryContainer = Color(0xFFD5F3E8),
            secondary = Color(0xFFB7CDC4),
            onSecondary = Color(0xFF22312C),
            secondaryContainer = Color(0xFF2A3B35),
            tertiary = Color(0xFFFFB4A8),
            onTertiary = Color(0xFF5C1A14),
            background = Color(0xFF0C1613),
            onBackground = Color(0xFFE7F2ED),
            surface = Color(0xFF14211C),
            onSurface = Color(0xFFE7F2ED),
            surfaceVariant = Color(0xFF24332D),
            onSurfaceVariant = Color(0xFFB7C7C0),
            outline = Color(0xFF3C5148),
        )
    } else {
        lightColorScheme(
            primary = NsBrand,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD7EFE7),
            onPrimaryContainer = NsInk,
            secondary = Color(0xFF3E5C54),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD7E6E1),
            onSecondaryContainer = NsInk,
            tertiary = Clay,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF8E8E6),
            background = NsPaper,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceVariant = Color(0xFFE6EFEC),
            onSurfaceVariant = Color(0xFF4A5C55),
            outline = Color(0xFFD3E0DB),
            outlineVariant = Color(0xFFE4EEE9),
        )
    }
    val extra = ExtraColors(
        get = recovery,
        give = sell,
        amber = if (dark) Color(0xFFE7C27A) else Amber,
        softGet = recovery.soften(dark),
        softGive = sell.soften(dark),
        softAmber = if (dark) Color(0xFF3A311C) else Color(0xFFF8F1E4),
        tile = if (dark) Color(0xFF1B2B25) else Color.White,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = type(), shapes = NsShapes, content = content)
    }
}
