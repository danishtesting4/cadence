package app.cadence.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Accent = Color(0xFFC7B4FF)
val AccentDeep = Color(0xFF8B6CF6)
val Sky = Color(0xFF89D7F5)

val Bg0 = Color(0xFF0A0A0F)
val Bg1 = Color(0xFF101017)
val Bg2 = Color(0xFF16161F)

val Surface1 = Color(0xFF17171F)
val Surface2 = Color(0xFF1F1F2B)
val Surface3 = Color(0xFF2A2A3A)

val TextPrimary = Color(0xFFF2F1F8)
val TextSecondary = Color(0xFF9C9CB2)
val TextTertiary = Color(0xFF6E6E85)

val Hairline = Color(0x1AFFFFFF)

val AppBackground = Brush.verticalGradient(
    listOf(Color(0xFF0D0C14), Bg0, Color(0xFF0B0A12)),
)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1B1030),
    primaryContainer = AccentDeep,
    onPrimaryContainer = Color(0xFFF6F1FF),
    secondary = Sky,
    onSecondary = Color(0xFF07222C),
    background = Bg0,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A3A4D),
    error = Color(0xFFFF9A9A),
    onError = Color(0xFF3A0A0A),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun CadenceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        content = content,
    )
}
