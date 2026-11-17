package com.ffmpegplaydownloader.app.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ffmpegplaydownloader.app.data.YtDlpRepository
import com.ffmpegplaydownloader.app.model.AudioFilterMode
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.model.PlaybackExtrasState
import com.ffmpegplaydownloader.app.model.StreamMode
import com.ffmpegplaydownloader.app.player.PlaybackExtrasManager
import com.ffmpegplaydownloader.app.player.RepeatModeOption
import com.ffmpegplaydownloader.app.player.SleepTimerPreset
import com.ffmpegplaydownloader.app.ui.theme.AppColors
import com.ffmpegplaydownloader.app.viewmodel.MainUiState
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun PlayerSection(
    state: MainUiState,
    exoPlayer: ExoPlayer,
    isFavorite: Boolean,
    isPipSupported: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onSeekByMs: (Long) -> Unit,
    onToggleSubtitles: () -> Unit,
    onStreamModeChange: (StreamMode) -> Unit,
    onAudioFilterChange: (AudioFilterMode) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimerAndSpeed: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenDownload: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val current = state.currentMedia
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.BgSurface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
    ) {
        // Video / Audio Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (state.isFullscreen) Modifier.height(340.dp)
                    else Modifier.aspectRatio(16f / 9f)
                )
                .background(Color(0xFF05070B)),
            contentAlignment = Alignment.Center
        ) {
            if (current == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text("🎬", fontSize = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Připraveno k přehrávání",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Vyhledejte video či hudbu nebo vložte URL odkaz.",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (state.streamMode == StreamMode.AUDIO_ONLY && !current.isLocal) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0A0E17).copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            if (current.thumbnail.isNotBlank()) {
                                AsyncImage(
                                    model = current.thumbnail,
                                    contentDescription = current.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(116.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            2.dp,
                                            platformAccentColor(current.platform).copy(alpha = 0.7f),
                                            RoundedCornerShape(14.dp)
                                        )
                                )
                            } else {
                                Text("🎧", fontSize = 40.sp)
                            }
                            PlatformBadge(
                                platform = current.platform,
                                extractor = current.extractor,
                                contentKind = current.contentKind.ifBlank { "Audio" },
                                solid = false
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPipSupported) {
                        IconButton(
                            onClick = onEnterPip,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Obraz v obraze (PiP)",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = if (state.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Celá obrazovka",
                            tint = Color.White
                        )
                    }
                }

                if (state.isResolvingStream) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppColors.PrimaryBlue)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Načítám stream přes yt-dlp...",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (state.streamError != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(AppColors.AccentRed.copy(alpha = 0.9f))
                            .padding(8.dp)
                    ) {
                        Text(state.streamError, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Seek bar & quick jump buttons
        if (current != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.BgElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val totalMs = state.totalDurationMs.coerceAtLeast(1L)
                val sliderValue = if (isDraggingSlider) {
                    dragFraction
                } else {
                    (state.currentPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                }
                val displayCurrentSec = if (isDraggingSlider) {
                    ((dragFraction * totalMs) / 1000L).toLong()
                } else {
                    state.currentPositionMs / 1000L
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = YtDlpRepository.formatDuration(displayCurrentSec).ifEmpty { "0:00" },
                        color = AppColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isDraggingSlider = true
                            dragFraction = it
                        },
                        onValueChangeFinished = {
                            val target = (dragFraction * totalMs).toLong()
                            isDraggingSlider = false
                            onSeekToMs(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.PrimaryBlue,
                            activeTrackColor = AppColors.PrimaryBlue,
                            inactiveTrackColor = AppColors.Border
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = YtDlpRepository.formatDuration(state.totalDurationMs / 1000L)
                            .ifEmpty { current.durationString.ifEmpty { "0:00" } },
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                SeekControlsRow(
                    isPlaying = state.isPlaying,
                    subText = when {
                        current.isLocal -> "💬 Titulky: Lokální"
                        !state.currentSubInfo.available -> "💬 CZ: Nedostupné"
                        state.subtitlesEnabled -> "💬 CZ: Zap (${state.currentSubInfo.label})"
                        else -> "💬 CZ: Vyp"
                    },
                    subsEnabled = state.currentSubInfo.available && !current.isLocal,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekToStart = { onSeekToMs(0L) },
                    onSeekByMs = onSeekByMs,
                    onToggleSubtitles = onToggleSubtitles
                )
            }
        }

        // Now playing + Stream Mode + Audio Filter + Favorite + Download
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (current != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    PlatformBadge(
                        platform = if (current.isLocal) MediaPlatform.LOCAL else current.platform,
                        extractor = current.extractor,
                        contentKind = current.contentKind
                    )
                }
            }
            Text(
                text = current?.title ?: "Nic se nepřehrává",
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    current == null -> "Vyberte položku z vyhledávání, fronty, oblíbených nebo historie"
                    current.isLocal -> "📁 Lokální soubor (${current.ext})"
                    else -> "${current.channel}${if (current.durationString.isNotBlank()) " • ${current.durationString}" else ""}"
                },
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            StreamOptionsBar(
                currentExists = current != null,
                isLocal = current?.isLocal == true,
                isFavorite = isFavorite,
                isPipSupported = isPipSupported,
                streamMode = state.streamMode,
                audioFilter = state.audioFilter,
                eqEnabled = state.equalizerState.enabled,
                eqPresetLabel = state.equalizerState.preset.label,
                playbackExtras = state.playbackExtras,
                onStreamModeChange = onStreamModeChange,
                onAudioFilterChange = onAudioFilterChange,
                onOpenEqualizer = onOpenEqualizer,
                onOpenSleepTimerAndSpeed = onOpenSleepTimerAndSpeed,
                onEnterPip = onEnterPip,
                onToggleFavorite = onToggleFavorite,
                onOpenDownload = onOpenDownload,
                onToggleFullscreen = onToggleFullscreen
            )
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeekControlsRow(
    isPlaying: Boolean,
    subText: String,
    subsEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekToStart: () -> Unit,
    onSeekByMs: (Long) -> Unit,
    onToggleSubtitles: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SmallControlChip("⏮ 0:00", onClick = onSeekToStart)
        SmallControlChip("-30s", onClick = { onSeekByMs(-30_000L) })
        SmallControlChip("-10s", onClick = { onSeekByMs(-10_000L) })
        FilledTonalButton(
            onClick = onTogglePlayPause,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = AppColors.PrimaryBlue,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
        SmallControlChip("+10s", onClick = { onSeekByMs(10_000L) })
        SmallControlChip("+30s", onClick = { onSeekByMs(30_000L) })
        OutlinedButton(
            onClick = onToggleSubtitles,
            enabled = subsEnabled,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(subText, fontSize = 11.sp, color = if (subsEnabled) AppColors.TextPrimary else AppColors.TextSecondary)
        }
    }
}

@Composable
private fun SmallControlChip(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = AppColors.BgSurface,
            contentColor = AppColors.TextPrimary
        )
    ) {
        Text(label, fontSize = 11.sp)
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamOptionsBar(
    currentExists: Boolean,
    isLocal: Boolean,
    isFavorite: Boolean,
    isPipSupported: Boolean,
    streamMode: StreamMode,
    audioFilter: AudioFilterMode,
    eqEnabled: Boolean,
    eqPresetLabel: String,
    playbackExtras: PlaybackExtrasState,
    onStreamModeChange: (StreamMode) -> Unit,
    onAudioFilterChange: (AudioFilterMode) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimerAndSpeed: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenDownload: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    var modeExpanded by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    val extrasBadge = formatPlaybackExtrasBadge(playbackExtras)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            OutlinedButton(
                onClick = { modeExpanded = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(streamMode.label, fontSize = 11.sp, color = AppColors.TextPrimary)
            }
            DropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false },
                modifier = Modifier.background(AppColors.BgElevated)
            ) {
                StreamMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label, color = AppColors.TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            modeExpanded = false
                            onStreamModeChange(mode)
                        }
                    )
                }
            }
        }

        Box {
            OutlinedButton(
                onClick = { filterExpanded = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(audioFilter.label, fontSize = 11.sp, color = AppColors.TextPrimary)
            }
            DropdownMenu(
                expanded = filterExpanded,
                onDismissRequest = { filterExpanded = false },
                modifier = Modifier.background(AppColors.BgElevated)
            ) {
                AudioFilterMode.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter.label, color = AppColors.TextPrimary, fontSize = 13.sp) },
                        onClick = {
                            filterExpanded = false
                            onAudioFilterChange(filter)
                        }
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = onOpenEqualizer,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (eqEnabled) AppColors.PrimaryBlue.copy(alpha = 0.25f) else AppColors.BgElevated,
                contentColor = if (eqEnabled) AppColors.PrimaryBlue else AppColors.TextPrimary
            )
        ) {
            Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (eqEnabled) "EQ: $eqPresetLabel" else "Equalizér",
                fontSize = 11.sp
            )
        }

        FilledTonalButton(
            onClick = onOpenSleepTimerAndSpeed,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = when {
                    extrasBadge.isTimerActive -> AppColors.WarningAmber.copy(alpha = 0.22f)
                    extrasBadge.isAnyActive -> AppColors.PrimaryBlue.copy(alpha = 0.25f)
                    else -> AppColors.BgElevated
                },
                contentColor = when {
                    extrasBadge.isTimerActive -> AppColors.WarningAmber
                    extrasBadge.isAnyActive -> AppColors.PrimaryBlue
                    else -> AppColors.TextPrimary
                }
            )
        ) {
            Text(extrasBadge.label, fontSize = 11.sp, fontWeight = if (extrasBadge.isAnyActive) FontWeight.SemiBold else FontWeight.Normal)
        }

        if (isPipSupported) {
            OutlinedButton(
                onClick = onEnterPip,
                enabled = currentExists,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppColors.TextPrimary)
                Spacer(Modifier.width(4.dp))
                Text("PiP", fontSize = 11.sp, color = AppColors.TextPrimary)
            }
        }

        OutlinedButton(
            onClick = onToggleFullscreen,
            enabled = currentExists,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppColors.TextPrimary)
            Spacer(Modifier.width(4.dp))
            Text("Na šířku", fontSize = 11.sp, color = AppColors.TextPrimary)
        }

        OutlinedButton(
            onClick = onToggleFavorite,
            enabled = currentExists && !isLocal,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (isFavorite) AppColors.GoldStar else AppColors.TextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (isFavorite) "V oblíbených" else "Oblíbené",
                fontSize = 11.sp,
                color = if (isFavorite) AppColors.GoldStar else AppColors.TextPrimary
            )
        }

        Button(
            onClick = onOpenDownload,
            enabled = currentExists && !isLocal,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.AccentRed,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Stáhnout / FFmpeg", fontSize = 11.sp)
        }
    }
}

internal data class PlaybackExtrasBadgeInfo(
    val label: String,
    val isTimerActive: Boolean,
    val isAnyActive: Boolean
)

internal fun formatPlaybackExtrasBadge(extras: PlaybackExtrasState): PlaybackExtrasBadgeInfo {
    val hasSpeed = abs(extras.playbackSpeed - 1.0f) > 0.01f
    val hasRepeat = extras.repeatMode != RepeatModeOption.OFF
    val hasShuffle = extras.shuffleEnabled
    val hasTimer = extras.sleepTimerPreset != SleepTimerPreset.OFF

    val parts = buildList {
        if (hasSpeed) {
            add(PlaybackExtrasManager.formatSpeedLabel(extras.playbackSpeed))
        }
        if (hasRepeat) {
            add(if (extras.repeatMode == RepeatModeOption.ONE) "🔁1" else "🔁")
        }
        if (hasShuffle) {
            add("🔀")
        }
        if (extras.sleepTimerPreset == SleepTimerPreset.END_OF_TRACK) {
            add("Po skladbě")
        } else if (hasTimer && extras.sleepTimerRemainingSec > 0L) {
            add(PlaybackExtrasManager.formatRemainingTime(extras.sleepTimerRemainingSec))
        } else if (hasTimer) {
            add(extras.sleepTimerPreset.label)
        }
    }

    val isAnyActive = hasSpeed || hasRepeat || hasShuffle || hasTimer
    val label = if (parts.isEmpty()) {
        "⏱ Časovač / Rychlost"
    } else {
        "⏱ ${parts.joinToString(" • ")}"
    }
    return PlaybackExtrasBadgeInfo(
        label = label,
        isTimerActive = hasTimer,
        isAnyActive = isAnyActive
    )
}
