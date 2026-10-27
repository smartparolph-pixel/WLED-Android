package ca.cgagnier.wlednativeandroid.ui.brand

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.ui.theme.ThemeViewModel

/** Sinag brand tokens. Used only by the Sinag account screens; WLED device screens keep their theme. */
@Suppress("MagicNumber") // Hex color literals are the brand tokens themselves.
object SinagColors {
    val SignalGold = Color(0xFFE4A82E)
    val LanternNavy = Color(0xFF122340)
    val GoldTint = Color(0xFFF3C875)
    val Paper = Color(0xFFFAF7EF)
    val Slate = Color(0xFF5B6275)
    val MutedOnDark = Color(0xFFB7C0D4)
    val DarkFieldFill = Color(0xFF1C3157)

    // Field borders darkened from #D9D2BE to reach 3:1 against the surface (WCAG 1.4.11).
    val FieldBorderLight = Color(0xFF8F8A7C)
    val FieldBorderDark = Color(0xFF7A89AB)
    val DangerLight = Color(0xFFB3261E)
    val DangerDark = Color(0xFFF2B8B5)
}

/** Brand values Material's color scheme has no slot for. */
@Immutable
data class SinagExtras(
    val isDark: Boolean,
    val fieldFill: Color,
    /** Muted text: Slate on Paper, #B7C0D4 on navy. */
    val muted: Color,
    /** The tagline: Slate on Paper, Gold Tint on navy (never gold text on Paper). */
    val tagline: Color,
    val lockup: Int,
)

val LocalSinagExtras = staticCompositionLocalOf {
    SinagExtras(false, Color.White, SinagColors.Slate, SinagColors.Slate, R.drawable.sinag_lockup_color)
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val lightColors = lightColorScheme(
    primary = SinagColors.SignalGold,
    onPrimary = SinagColors.LanternNavy,
    background = SinagColors.Paper,
    onBackground = SinagColors.LanternNavy,
    surface = SinagColors.Paper,
    onSurface = SinagColors.LanternNavy,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    onSurfaceVariant = SinagColors.Slate,
    outline = SinagColors.FieldBorderLight,
    error = SinagColors.DangerLight,
    onError = Color.White,
)

private val darkColors = darkColorScheme(
    primary = SinagColors.SignalGold,
    onPrimary = SinagColors.LanternNavy,
    background = SinagColors.LanternNavy,
    onBackground = SinagColors.Paper,
    surface = SinagColors.LanternNavy,
    onSurface = SinagColors.Paper,
    surfaceContainer = SinagColors.DarkFieldFill,
    surfaceContainerHigh = SinagColors.DarkFieldFill,
    onSurfaceVariant = SinagColors.MutedOnDark,
    outline = SinagColors.FieldBorderDark,
    error = SinagColors.DangerDark,
    onError = SinagColors.LanternNavy,
)

private fun TextStyle.poppins(weight: FontWeight) = copy(fontFamily = Poppins, fontWeight = weight)

/** Poppins everywhere: Bold headings, Medium buttons and labels, Regular body. Sizes stay in sp for font scaling. */
private val sinagTypography = Typography().run {
    copy(
        displaySmall = displaySmall.poppins(FontWeight.Bold),
        headlineMedium = headlineMedium.poppins(FontWeight.Bold),
        headlineSmall = headlineSmall.poppins(FontWeight.Bold).copy(fontSize = 24.sp),
        titleLarge = titleLarge.poppins(FontWeight.Bold),
        titleMedium = titleMedium.poppins(FontWeight.Medium),
        bodyLarge = bodyLarge.poppins(FontWeight.Normal),
        bodyMedium = bodyMedium.poppins(FontWeight.Normal),
        bodySmall = bodySmall.poppins(FontWeight.Normal),
        labelLarge = labelLarge.poppins(FontWeight.Medium).copy(fontSize = 16.sp),
        labelMedium = labelMedium.poppins(FontWeight.Medium),
        labelSmall = labelSmall.poppins(FontWeight.Medium),
    )
}

/** Follows the app's Light/Dark/Auto setting, like [ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme]. */
@Composable
fun SinagTheme(themeViewModel: ThemeViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val theme by themeViewModel.theme.collectAsStateWithLifecycle()
    val darkTheme = when (theme) {
        ThemeSettings.Auto -> isSystemInDarkTheme()
        ThemeSettings.Dark -> true
        else -> false
    }
    SinagTheme(darkTheme = darkTheme, content = content)
}

@Composable
fun SinagTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val extras = if (darkTheme) {
        SinagExtras(
            isDark = true,
            fieldFill = SinagColors.DarkFieldFill,
            muted = SinagColors.MutedOnDark,
            tagline = SinagColors.GoldTint,
            lockup = R.drawable.sinag_lockup_reversed,
        )
    } else {
        SinagExtras(
            isDark = false,
            fieldFill = Color.White,
            muted = SinagColors.Slate,
            tagline = SinagColors.Slate,
            lockup = R.drawable.sinag_lockup_color,
        )
    }
    CompositionLocalProvider(LocalSinagExtras provides extras) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColors else lightColors,
            typography = sinagTypography,
            content = content,
        )
    }
}

/** Links, kept in one place. */
object SinagLinks {
    const val PRIVACY_POLICY_URL = "https://sinagtechlabs.com/privacy.html"

    /** Hide the Terms of Use row while this is null. */
    val TERMS_OF_USE_URL: String? = null

    /** GPL-3.0 requires offering the source of this fork. */
    const val SOURCE_CODE_URL = "https://github.com/smartparolph-pixel/WLED-Android"
}
