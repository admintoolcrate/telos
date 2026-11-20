package cc.toolcrate.telos.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.toolcrate.telos.R

/**
 * The palette, as Mono: the ToolCrate family look (docs/family-look.md), shared with Stagira,
 * Lyceum and Kairos. Black on white, true black at night. `accent` is ink; `accentSoft` is the
 * soft tint for selected things. Priority colours are the one exception to "no colour", and
 * always come with a text or shape cue. (The class keeps its old name to keep the diff small.)
 */
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
    page = Color(0xFFFFFFFF), card = Color(0xFFF7F7F6), ink = Color(0xFF111111),
    muted = Color(0xFF6E6E6E), rule = Color(0xFFEAEAE8), accent = Color(0xFF111111),
    accentSoft = Color(0xFFECECEA),
    p1 = Color(0xFF9C3D2A), p2 = Color(0xFF9A6B12), p3 = Color(0xFF3F5E7A), isDark = false,
)

val DarkPaper = Paper(
    page = Color(0xFF000000), card = Color(0xFF0E0E0E), ink = Color(0xFFFFFFFF),
    muted = Color(0xFF9A9A9A), rule = Color(0xFF1C1C1C), accent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF262626),
    p1 = Color(0xFFE08B72), p2 = Color(0xFFD9B060), p3 = Color(0xFF93B1CE), isDark = true,
)

val Garamond = FontFamily(
    Font(R.font.eb_garamond_regular, FontWeight.Normal),
    Font(R.font.eb_garamond_medium, FontWeight.Medium),
    Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Inter at the family's weights, from its variable axis (as in Stagira). */
val Inter = FontFamily(inter(FontWeight.Light), inter(FontWeight.Normal), inter(FontWeight.Medium), inter(FontWeight.SemiBold))

/** A screen's title, in Garamond, as Stagira sets its top bars (docs/family-look.md). */
val ScreenTitle = TextStyle(fontFamily = Garamond, fontSize = 30.sp, lineHeight = 34.sp)

/** The family type scale (docs/family-look.md): Garamond for display moments, Inter for the rest. */
private fun typography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Garamond, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
        displayMedium = base.displayMedium.copy(fontFamily = Garamond, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
        displaySmall = base.displaySmall.copy(fontFamily = Garamond, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
        headlineLarge = base.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Light, letterSpacing = (-0.6).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Light, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Inter, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = Inter, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontFamily = Inter, letterSpacing = 0.1.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelSmall = base.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

/** Family shapes (docs/family-look.md): Material's small parts, 24 dp cards, 28 dp sheets. */
val FamilyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun Paper.scheme(): ColorScheme = if (isDark) darkColorScheme(
    primary = accent, onPrimary = page, primaryContainer = accentSoft, onPrimaryContainer = ink,
    secondary = muted, onSecondary = page, secondaryContainer = accentSoft, onSecondaryContainer = ink,
    background = page, onBackground = ink, surface = page, onSurface = ink,
    surfaceVariant = card, onSurfaceVariant = muted, surfaceContainer = card,
    surfaceContainerLow = card, surfaceContainerHigh = card, surfaceContainerHighest = card,
    surfaceContainerLowest = page,
    outline = muted, outlineVariant = rule, error = ink, onError = page,
) else lightColorScheme(
    primary = accent, onPrimary = page, primaryContainer = accentSoft, onPrimaryContainer = ink,
    secondary = muted, onSecondary = page, secondaryContainer = accentSoft, onSecondaryContainer = ink,
    background = page, onBackground = ink, surface = page, onSurface = ink,
    surfaceVariant = card, onSurfaceVariant = muted, surfaceContainer = card,
    surfaceContainerLow = card, surfaceContainerHigh = card, surfaceContainerHighest = card,
    surfaceContainerLowest = page,
    outline = muted, outlineVariant = rule, error = ink, onError = page,
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
        MaterialTheme(colorScheme = paper.scheme(), typography = typography(), shapes = FamilyShapes, content = content)
    }
}
