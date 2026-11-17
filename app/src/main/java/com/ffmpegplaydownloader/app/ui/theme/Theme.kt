package com.ffmpegplaydownloader.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val BgMain = Color(0xFF0B0F17)
    val BgSurface = Color(0xFF151B26)
    val BgElevated = Color(0xFF1E2636)
    val BgHover = Color(0xFF263044)
    val Border = Color(0xFF2A3448)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val AccentRed = Color(0xFFEF4444)
    val AccentOrange = Color(0xFFF97316)
    val PrimaryBlue = Color(0xFF3B82F6)
    val SuccessGreen = Color(0xFF10B981)
    val WarningAmber = Color(0xFFF59E0B)
    val GoldStar = Color(0xFFFBBF24)
    val YouTubeRed = Color(0xFFEF4444)
    val YouTubeMusicPink = Color(0xFFEC4899)
    val OtherPlatformTeal = Color(0xFF06B6D4)
}

private val StudioDarkColorScheme = darkColorScheme(
    primary = AppColors.PrimaryBlue,
    onPrimary = Color.White,
    secondary = AppColors.AccentRed,
    onSecondary = Color.White,
    tertiary = AppColors.WarningAmber,
    background = AppColors.BgMain,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.BgSurface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.BgElevated,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Border,
    error = AppColors.AccentRed
)

@Composable
fun FfmpegPlayDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        content = content
    )
}
