package com.ffmpegplaydownloader.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ffmpegplaydownloader.app.data.YtDlpRepository
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.model.StreamMode
import com.ffmpegplaydownloader.app.ui.theme.AppTheme
import com.ffmpegplaydownloader.app.viewmodel.MainUiState
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun FullscreenPlayerOverlay(
    state: MainUiState,
    exoPlayer: ExoPlayer,
    isPipSupported: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onSeekByMs: (Long) -> Unit,
    onToggleSubtitles: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimerAndSpeed: () -> Unit,
    onOpenShare: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleZoom: () -> Unit,
    onNextInQueue: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val current = state.currentMedia
    val colors = AppTheme.colors

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    BackHandler(enabled = true) {
        onExitFullscreen()
    }

    DisposableEffect(activity) {
        val window = activity?.window
        if (activity != null && window != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (activity != null && window != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(
        controlsVisible,
        state.isPlaying,
        isDraggingSlider,
        interactionTick,
        state.showEqualizerDialog,
        state.showSleepTimerDialog,
        state.shareModalTarget
    ) {
        if (
            controlsVisible &&
            state.isPlaying &&
            !isDraggingSlider &&
            !state.showEqualizerDialog &&
            !state.showSleepTimerDialog &&
            state.shareModalTarget == null
        ) {
            delay(4000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
                interactionTick++
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (state.fullscreenCropZoom) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
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
                val targetResize = if (state.fullscreenCropZoom) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                if (view.resizeMode != targetResize) {
                    view.resizeMode = targetResize
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Landscape music/audio stage when in AUDIO_ONLY mode
        if (current != null && state.streamMode == StreamMode.AUDIO_ONLY && !current.isLocal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF172033), Color(0xFF06090F))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(horizontal = 48.dp)
                ) {
                    if (current.thumbnail.isNotBlank()) {
                        AsyncImage(
                            model = current.thumbnail,
                            contentDescription = current.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(164.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    2.dp,
                                    platformAccentColor(current.platform).copy(alpha = 0.75f),
                                    RoundedCornerShape(16.dp)
                                )
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlatformBadge(
                            platform = current.platform,
                            extractor = current.extractor,
                            contentKind = current.contentKind.ifBlank { "Audio" },
                            solid = true
                        )
                        Text(
                            current.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (current.channel.isNotBlank()) {
                            Text(
                                current.channel,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
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
                    CircularProgressIndicator(color = colors.primaryBlue)
                    Spacer(Modifier.height(8.dp))
                    Text("Načítám stream přes yt-dlp...", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // HUD controls overlay
        AnimatedVisibility(
            visible = controlsVisible || !state.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onExitFullscreen,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                Icons.Default.FullscreenExit,
                                contentDescription = "Ukončit celou obrazovku",
                                tint = Color.White
                            )
                        }
                        if (current != null) {
                            PlatformBadge(
                                platform = if (current.isLocal) MediaPlatform.LOCAL else current.platform,
                                extractor = current.extractor,
                                contentKind = current.contentKind,
                                solid = true
                            )
                            Text(
                                text = current.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .widthIn(max = 480.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        if (state.currentSubInfo.available && current?.isLocal == false) {
                            OutlinedButton(
                                onClick = {
                                    interactionTick++
                                    onToggleSubtitles()
                                },
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.55f),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    if (state.subtitlesEnabled) "💬 CZ: Zap" else "💬 CZ: Vyp",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }

                        val extrasBadge = formatPlaybackExtrasBadge(state.playbackExtras)
                        FilledTonalButton(
                            onClick = {
                                interactionTick++
                                onOpenSleepTimerAndSpeed()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = when {
                                    extrasBadge.isTimerActive -> colors.warningAmber.copy(alpha = 0.88f)
                                    extrasBadge.isAnyActive -> colors.primaryBlue.copy(alpha = 0.88f)
                                    else -> Color.Black.copy(alpha = 0.6f)
                                },
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(extrasBadge.label, fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                interactionTick++
                                onOpenEqualizer()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (state.equalizerState.enabled) {
                                    colors.primaryBlue.copy(alpha = 0.88f)
                                } else {
                                    Color.Black.copy(alpha = 0.6f)
                                },
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (state.equalizerState.enabled) "EQ: ${state.equalizerState.preset.label}" else "Equalizér",
                                fontSize = 11.sp
                            )
                        }

                        if (current != null) {
                            FilledTonalButton(
                                onClick = {
                                    interactionTick++
                                    onOpenShare()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.6f),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Cast, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sdílet / Cast", fontSize = 11.sp)
                            }
                        }

                        if (isPipSupported) {
                            FilledTonalButton(
                                onClick = {
                                    interactionTick++
                                    onEnterPip()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.6f),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PiP", fontSize = 11.sp)
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                interactionTick++
                                onToggleZoom()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.fullscreenCropZoom) "Vyplnit" else "Přizpůsobit", fontSize = 11.sp)
                        }
                    }
                }

                // Center transport controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            interactionTick++
                            onSeekByMs(-10_000L)
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    IconButton(
                        onClick = {
                            interactionTick++
                            onTogglePlayPause()
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(colors.primaryBlue.copy(alpha = 0.9f))
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Přehrát / Pozastavit",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            interactionTick++
                            onSeekByMs(10_000L)
                        },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                // Bottom seekbar & quick jumps
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    val totalMs = state.totalDurationMs.coerceAtLeast(1L)
                    val sliderValue = if (isDraggingSlider) {
                        dragFraction
                    } else {
                        (state.currentPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                    }
                    val displaySec = if (isDraggingSlider) {
                        ((dragFraction * totalMs) / 1000L).toLong()
                    } else {
                        state.currentPositionMs / 1000L
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            YtDlpRepository.formatDuration(displaySec).ifEmpty { "0:00" },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                isDraggingSlider = true
                                dragFraction = it
                                interactionTick++
                            },
                            onValueChangeFinished = {
                                val target = (dragFraction * totalMs).toLong()
                                isDraggingSlider = false
                                interactionTick++
                                onSeekToMs(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primaryBlue,
                                activeTrackColor = colors.primaryBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            YtDlpRepository.formatDuration(state.totalDurationMs / 1000L)
                                .ifEmpty { current?.durationString?.ifEmpty { "0:00" } ?: "0:00" },
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        if (state.queue.size > 1) {
                            IconButton(
                                onClick = {
                                    interactionTick++
                                    onNextInQueue()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Další ve frontě", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
