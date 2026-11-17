package com.ffmpegplaydownloader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.ui.theme.AppColors

fun platformAccentColor(platform: MediaPlatform): Color = when (platform) {
    MediaPlatform.YOUTUBE_MUSIC -> AppColors.YouTubeMusicPink
    MediaPlatform.YOUTUBE -> AppColors.YouTubeRed
    MediaPlatform.OTHER -> AppColors.OtherPlatformTeal
    MediaPlatform.LOCAL -> AppColors.SuccessGreen
}

@Composable
fun PlatformBadge(
    platform: MediaPlatform,
    extractor: String = "",
    contentKind: String = "",
    solid: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = platformAccentColor(platform)
    val label = when (platform) {
        MediaPlatform.YOUTUBE_MUSIC -> "🎧 YT Music"
        MediaPlatform.YOUTUBE -> "▶ YouTube"
        MediaPlatform.LOCAL -> "📁 Lokální"
        MediaPlatform.OTHER -> "🌐 ${extractor.ifBlank { "Web" }}"
    }
    val kindSuffix = if (contentKind.isNotBlank() && platform == MediaPlatform.YOUTUBE_MUSIC) {
        " • $contentKind"
    } else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (solid) accent.copy(alpha = 0.92f) else accent.copy(alpha = 0.16f))
            .then(
                if (!solid) Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                else Modifier
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label + kindSuffix,
            color = if (solid) Color.White else accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
