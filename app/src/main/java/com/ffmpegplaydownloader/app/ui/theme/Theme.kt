package com.ffmpegplaydownloader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColorPalette(
    val bgMain: Color,
    val bgSurface: Color,
    val bgElevated: Color,
    val bgHover: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentRed: Color,
    val accentOrange: Color,
    val primaryBlue: Color,
    val successGreen: Color,
    val warningAmber: Color,
    val goldStar: Color,
    val youTubeRed: Color,
    val youTubeMusicPink: Color,
    val otherPlatformTeal: Color,
    val isDark: Boolean
)

val DarkAppColors = AppColorPalette(
    bgMain = Color(0xFF0B0F17),
    bgSurface = Color(0xFF151B26),
    bgElevated = Color(0xFF1E2636),
    bgHover = Color(0xFF263044),
    border = Color(0xFF2A3448),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    accentRed = Color(0xFFEF4444),
    accentOrange = Color(0xFFF97316),
    primaryBlue = Color(0xFF3B82F6),
    successGreen = Color(0xFF10B981),
    warningAmber = Color(0xFFF59E0B),
    goldStar = Color(0xFFFBBF24),
    youTubeRed = Color(0xFFEF4444),
    youTubeMusicPink = Color(0xFFEC4899),
    otherPlatformTeal = Color(0xFF06B6D4),
    isDark = true
)

val LightAppColors = AppColorPalette(
    bgMain = Color(0xFFF8FAFC),
    bgSurface = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFF1F5F9),
    bgHover = Color(0xFFE2E8F0),
    border = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    accentRed = Color(0xFFDC2626),
    accentOrange = Color(0xFFEA580C),
    primaryBlue = Color(0xFF2563EB),
    successGreen = Color(0xFF059669),
    warningAmber = Color(0xFFD97706),
    goldStar = Color(0xFFD97706),
    youTubeRed = Color(0xFFDC2626),
    youTubeMusicPink = Color(0xFFDB2777),
    otherPlatformTeal = Color(0xFF0891B2),
    isDark = false
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

object AppTheme {
    val colors: AppColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

object AppColors {
    val BgMain: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgMain
    val BgSurface: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgSurface
    val BgElevated: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgElevated
    val BgHover: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.bgHover
    val Border: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.border
    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
    val TextSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
    val AccentRed: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentRed
    val AccentOrange: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.accentOrange
    val PrimaryBlue: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryBlue
    val SuccessGreen: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.successGreen
    val WarningAmber: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.warningAmber
    val GoldStar: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.goldStar
    val YouTubeRed: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.youTubeRed
    val YouTubeMusicPink: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.youTubeMusicPink
    val OtherPlatformTeal: Color
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.otherPlatformTeal
    val IsDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalAppColors.current.isDark
}

private val StudioDarkColorScheme = darkColorScheme(
    primary = DarkAppColors.primaryBlue,
    onPrimary = Color.White,
    primaryContainer = DarkAppColors.primaryBlue.copy(alpha = 0.22f),
    onPrimaryContainer = DarkAppColors.textPrimary,
    secondary = DarkAppColors.accentRed,
    onSecondary = Color.White,
    tertiary = DarkAppColors.warningAmber,
    onTertiary = Color.White,
    background = DarkAppColors.bgMain,
    onBackground = DarkAppColors.textPrimary,
    surface = DarkAppColors.bgSurface,
    onSurface = DarkAppColors.textPrimary,
    surfaceVariant = DarkAppColors.bgElevated,
    onSurfaceVariant = DarkAppColors.textSecondary,
    outline = DarkAppColors.border,
    outlineVariant = DarkAppColors.border,
    error = DarkAppColors.accentRed,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LightAppColors.primaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = LightAppColors.textPrimary,
    secondary = LightAppColors.accentRed,
    onSecondary = Color.White,
    tertiary = LightAppColors.warningAmber,
    onTertiary = Color.White,
    background = LightAppColors.bgMain,
    onBackground = LightAppColors.textPrimary,
    surface = LightAppColors.bgSurface,
    onSurface = LightAppColors.textPrimary,
    surfaceVariant = LightAppColors.bgElevated,
    onSurfaceVariant = LightAppColors.textSecondary,
    outline = LightAppColors.border,
    outlineVariant = LightAppColors.border,
    error = LightAppColors.accentRed,
    onError = Color.White
)

@Composable
fun FfmpegPlayDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme

    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
