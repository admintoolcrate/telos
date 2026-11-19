package cc.toolcrate.telos.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cc.toolcrate.telos.R

/** The "Paper" palette: warm cream pages and brown-black ink. */
@Immutable
data class Paper(
    val page: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val accent: Color,
    val accentSoft: Color,
    val p1: Color,
    val p2: Color,
    val p3: Color,
    val isDark: Boolean,
)

val LightPaper = Paper(
    page = Color(0xFFF4EEE1), card = Color(0xFFFBF8F1), ink = Color(0xFF2A2119),
    muted = Color(0xFF6F6354), rule = Color(0xFFDCD2C1), accent = Color(0xFF8B5E34),
    accentSoft = Color(0xFFEADBC5),
    p1 = Color(0xFF9C3D2A), p2 = Color(0xFF9A6B12), p3 = Color(0xFF3F5E7A), isDark = false,
)

val DarkPaper = Paper(
    page = Color(0xFF1B1712), card = Color(0xFF25201A), ink = Color(0xFFEEE5D4),
    muted = Color(0xFFA79A86), rule = Color(0xFF3A3229), accent = Color(0xFFD3A872),
    accentSoft = Color(0xFF3B3022),
    p1 = Color(0xFFE08B72), p2 = Color(0xFFD9B060), p3 = Color(0xFF93B1CE), isDark = true,
)

val Garamond = FontFamily(
    Font(R.font.eb_garamond_regular, FontWeight.Normal),
    Font(R.font.eb_garamond_medium, FontWeight.Medium),
    Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
)

val Inter = FontFamily(Font(R.font.inter))

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.serif() = copy(fontFamily = Garamond, fontWeight = FontWeight.Medium)
    fun TextStyle.sans() = copy(fontFamily = Inter)
    return Typography(
        displayLarge = base.displayLarge.serif(),
        displayMedium = base.displayMedium.serif(),
        displaySmall = base.displaySmall.serif(),
        headlineLarge = base.headlineLarge.serif(),
        headlineMedium = base.headlineMedium.serif(),
        headlineSmall = base.headlineSmall.serif(),
        titleLarge = base.titleLarge.serif().copy(fontSize = 24.sp),
        titleMedium = base.titleMedium.sans(),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(),
        labelMedium = base.labelMedium.sans(),
        labelSmall = base.labelSmall.sans(),
    )
}

private fun Paper.scheme(): ColorScheme = if (isDark) darkColorScheme(
    primary = accent, onPrimary = page, primaryContainer = accentSoft, onPrimaryContainer = ink,
    secondary = muted, onSecondary = page, secondaryContainer = accentSoft, onSecondaryContainer = ink,
    background = page, onBackground = ink, surface = page, onSurface = ink,
    surfaceVariant = card, onSurfaceVariant = muted, surfaceContainer = card,
    surfaceContainerLow = card, surfaceContainerHigh = card, surfaceContainerHighest = card,
    surfaceContainerLowest = page,
    outline = muted, outlineVariant = rule, error = p1, onError = page,
) else lightColorScheme(
    primary = accent, onPrimary = page, primaryContainer = accentSoft, onPrimaryContainer = ink,
    secondary = muted, onSecondary = page, secondaryContainer = accentSoft, onSecondaryContainer = ink,
    background = page, onBackground = ink, surface = page, onSurface = ink,
    surfaceVariant = card, onSurfaceVariant = muted, surfaceContainer = card,
    surfaceContainerLow = card, surfaceContainerHigh = card, surfaceContainerHighest = card,
    surfaceContainerLowest = page,
    outline = muted, outlineVariant = rule, error = p1, onError = page,
)

val LocalPaper = staticCompositionLocalOf { LightPaper }

/** True when animations should be skipped: the system's animator scale is 0, or the user asked. */
val LocalReduceMotion = staticCompositionLocalOf { false }

enum class ThemeMode { System, Light, Dark }

@Composable
fun TelosTheme(
    mode: ThemeMode = ThemeMode.System,
    reduceMotionOverride: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val paper = if (dark) DarkPaper else LightPaper
    val context = LocalContext.current
    val systemReduce = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalPaper provides paper,
        LocalReduceMotion provides (systemReduce || reduceMotionOverride),
    ) {
        MaterialTheme(colorScheme = paper.scheme(), typography = typography(), content = content)
    }
}
