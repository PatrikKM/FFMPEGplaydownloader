package com.ffmpegplaydownloader.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ffmpegplaydownloader.app.MainActivity
import com.ffmpegplaydownloader.app.data.CookiesStatus
import com.ffmpegplaydownloader.app.data.SearchPlan
import com.ffmpegplaydownloader.app.model.DownloadJob
import com.ffmpegplaydownloader.app.model.DownloadStatus
import com.ffmpegplaydownloader.app.model.LocalMediaFile
import com.ffmpegplaydownloader.app.model.MainTab
import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.model.ResultPlatformFilter
import com.ffmpegplaydownloader.app.model.SearchSourceFilter
import com.ffmpegplaydownloader.app.model.StreamMode
import com.ffmpegplaydownloader.app.player.PipHelper
import com.ffmpegplaydownloader.app.ui.theme.AppTheme
import com.ffmpegplaydownloader.app.viewmodel.MainUiState
import com.ffmpegplaydownloader.app.viewmodel.MainViewModel
import com.ffmpegplaydownloader.app.voice.VoiceSearchController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/patrikmohler"

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isPipSupported = remember(context) { PipHelper.isPipSupported(context) }

    val fallbackRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = VoiceSearchController.extractFallbackResult(result.data)
            if (!spoken.isNullOrBlank()) {
                viewModel.onVoiceResult(spoken)
            } else {
                viewModel.onVoiceError("Nepodařilo se rozpoznat řeč. Zkuste to prosím znovu.")
            }
        } else {
            viewModel.onVoiceCancelled()
        }
    }

    val launchSystemVoiceFallback: () -> Unit = {
        viewModel.prepareForSystemVoiceFallback()
        try {
            val intent = VoiceSearchController.createFallbackRecognizerIntent()
            fallbackRecognizerLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.onVoiceError("V zařízení není dostupná žádná aplikace pro rozpoznávání hlasu.")
        } catch (_: Exception) {
            viewModel.onVoiceError("Nepodařilo se spustit systémový mikrofon.")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && VoiceSearchController.isDirectSpeechAvailable(context)) {
            viewModel.startVoiceListening()
        } else {
            launchSystemVoiceFallback()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.voiceFallbackEvents.collect {
            launchSystemVoiceFallback()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.exitAppEvents.collect {
            activity?.finishAndRemoveTask()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelVoiceIfListening()
        }
    }

    val onMicClick: () -> Unit = {
        focusManager.clearFocus()
        if (state.isVoiceListening) {
            viewModel.cancelVoiceListening()
        } else if (VoiceSearchController.hasMicPermission(context)) {
            if (VoiceSearchController.isDirectSpeechAvailable(context)) {
                viewModel.startVoiceListening()
            } else {
                launchSystemVoiceFallback()
            }
        } else if (VoiceSearchController.isDirectSpeechAvailable(context)) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            launchSystemVoiceFallback()
        }
    }

    val triggerPip: () -> Unit = {
        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            mainActivity.enterPipMode(viewModel.isVideoPipMode())
        } else if (activity != null) {
            PipHelper.enterPip(activity, viewModel.isVideoPipMode())
        }
    }

    val activeDownloadCount = state.downloads.count {
        it.status == DownloadStatus.STARTING ||
            it.status == DownloadStatus.DOWNLOADING ||
            it.status == DownloadStatus.PROCESSING
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isInPipMode) {
            PipPlayerSurface(
                state = state,
                exoPlayer = viewModel.playerController.exoPlayer
            )
        } else if (state.isFullscreen) {
            FullscreenPlayerOverlay(
                state = state,
                exoPlayer = viewModel.playerController.exoPlayer,
                isPipSupported = isPipSupported,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekToMs = viewModel::seekToMs,
                onSeekByMs = viewModel::seekByMs,
                onToggleSubtitles = viewModel::toggleSubtitles,
                onOpenEqualizer = viewModel::openEqualizer,
                onOpenSleepTimerAndSpeed = viewModel::openSleepTimerDialog,
                onOpenShare = {
                    state.currentMedia?.let { viewModel.openShareModal(it) }
                },
                onEnterPip = triggerPip,
                onToggleZoom = viewModel::toggleFullscreenZoom,
                onNextInQueue = viewModel::playNextInQueue,
                onExitFullscreen = viewModel::exitFullscreen
            )
        } else {
            Scaffold(
                containerColor = colors.bgMain,
                bottomBar = {
                    BottomNavigationBar(
                        state = state,
                        activeDownloadCount = activeDownloadCount,
                        onSelectTab = viewModel::selectTab
                    )
                }
            ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.bgMain)
        ) {
            if (!state.isFullscreen) {
                TopStudioHeader(
                    ytdlpVersion = state.ytdlpVersion,
                    ffmpegVersion = state.ffmpegVersion,
                    isUpdating = state.isUpdatingYtDlp,
                    cookiesStatus = state.cookiesStatus,
                    onUpdateClick = viewModel::checkAndAutoUpdateYtDlp,
                    onCookiesClick = viewModel::openCookiesDialog,
                    onReadmeClick = viewModel::openReadmeDialog,
                    onBuyMeACoffeeClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(BUY_ME_A_COFFEE_URL)
                            ).apply {
                                if (context !is Activity) {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            viewModel.showStatusBanner("☕ V zařízení nebyl nalezen prohlížeč pro otevření $BUY_ME_A_COFFEE_URL")
                        } catch (_: Exception) {
                            viewModel.showStatusBanner("☕ Nepodařilo se otevřít odkaz $BUY_ME_A_COFFEE_URL")
                        }
                    },
                    onExitClick = viewModel::requestExitApp
                )

                AnimatedVisibility(visible = state.statusBanner != null) {
                    state.statusBanner?.let { banner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.primaryBlue.copy(alpha = if (colors.isDark) 0.16f else 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                banner,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::dismissBanner,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Zavřít",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = {
                            Text(
                                "Přirozený dotaz nebo URL...",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = if (state.searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { viewModel.updateSearchQuery("") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Vymazat hledání",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                viewModel.search()
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primaryBlue,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedContainerColor = colors.bgSurface,
                            unfocusedContainerColor = colors.bgSurface,
                            cursorColor = colors.primaryBlue
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = onMicClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (state.isVoiceListening) colors.accentRed else colors.bgElevated,
                            contentColor = if (state.isVoiceListening) Color.White else colors.textPrimary
                        ),
                        modifier = Modifier
                            .width(48.dp)
                            .height(54.dp)
                            .border(
                                1.dp,
                                if (state.isVoiceListening) colors.accentRed else colors.border,
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (state.isVoiceListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "Hlasové vyhledávání"
                        )
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.search()
                        },
                        enabled = !state.isSearching,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .width(52.dp)
                            .height(54.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Hledat", tint = Color.White)
                    }
                }

                AnimatedVisibility(visible = state.voiceError != null) {
                    state.voiceError?.let { errMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentRed.copy(alpha = if (colors.isDark) 0.16f else 0.10f))
                                .border(1.dp, colors.accentRed.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.MicOff,
                                contentDescription = null,
                                tint = colors.accentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = errMsg,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::dismissVoiceError,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Zavřít chybu hlasu",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                SearchOptionsStrip(
                    selectedSource = state.searchSourceFilter,
                    smartEnabled = state.smartSearchEnabled,
                    onSelectSource = viewModel::updateSearchSourceFilter,
                    onToggleSmart = viewModel::toggleSmartSearch
                )
            }

            // Main scrollable body (Player + Active Tab Content)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "player_section") {
                    PlayerSection(
                        state = state,
                        exoPlayer = viewModel.playerController.exoPlayer,
                        isFavorite = viewModel.isFavorite(state.currentMedia),
                        isPipSupported = isPipSupported,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSeekToMs = viewModel::seekToMs,
                        onSeekByMs = viewModel::seekByMs,
                        onToggleSubtitles = viewModel::toggleSubtitles,
                        onStreamModeChange = viewModel::changeStreamMode,
                        onAudioFilterChange = viewModel::changeAudioFilter,
                        onOpenEqualizer = viewModel::openEqualizer,
                        onOpenSleepTimerAndSpeed = viewModel::openSleepTimerDialog,
                        onEnterPip = triggerPip,
                        onToggleFavorite = {
                            state.currentMedia?.let { viewModel.toggleFavorite(it) }
                        },
                        onOpenShare = {
                            state.currentMedia?.let { viewModel.openShareModal(it) }
                        },
                        onOpenDownload = {
                            state.currentMedia?.let { viewModel.openDownloadModal(it) }
                        },
                        onToggleFullscreen = viewModel::toggleFullscreen
                    )
                }

                if (!state.isFullscreen) {
                    when (state.selectedTab) {
                        MainTab.SEARCH -> {
                            if (state.isSearching) {
                                item {
                                    val msg = when (state.searchSourceFilter) {
                                        SearchSourceFilter.YOUTUBE_MUSIC -> "Vyhledávám skladby na YouTube Music..."
                                        SearchSourceFilter.YOUTUBE -> "Vyhledávám videa na YouTube..."
                                        SearchSourceFilter.ALL -> "Chytré vyhledávání na YouTube a YouTube Music..."
                                    }
                                    LoadingCard(msg)
                                }
                            } else if (state.searchError != null) {
                                item {
                                    EmptyStateCard(state.searchError ?: "")
                                }
                            } else if (state.searchResults.isEmpty()) {
                                item {
                                    SearchQuickTagsCard(
                                        onSelectTag = { query ->
                                            viewModel.search(queryOverride = query)
                                        }
                                    )
                                }
                            } else {
                                val ytCount = state.searchResults.count { it.platform == MediaPlatform.YOUTUBE }
                                val ytmCount = state.searchResults.count { it.platform == MediaPlatform.YOUTUBE_MUSIC }
                                val otherCount = state.searchResults.count { it.platform == MediaPlatform.OTHER }

                                val displayedResults = when (state.resultPlatformFilter) {
                                    ResultPlatformFilter.ALL -> state.searchResults
                                    ResultPlatformFilter.YOUTUBE -> state.searchResults.filter { it.platform == MediaPlatform.YOUTUBE }
                                    ResultPlatformFilter.YOUTUBE_MUSIC -> state.searchResults.filter { it.platform == MediaPlatform.YOUTUBE_MUSIC }
                                    ResultPlatformFilter.OTHER -> state.searchResults.filter { it.platform == MediaPlatform.OTHER }
                                }

                                state.activeSearchPlan?.takeIf { it.isNaturalLanguage }?.let { plan ->
                                    item(key = "smart_plan") {
                                        SmartSearchPlanCard(
                                            plan = plan,
                                            onSearchLiteral = {
                                                viewModel.search(
                                                    queryOverride = plan.originalQuery,
                                                    bypassSmartNlp = true
                                                )
                                            }
                                        )
                                    }
                                }

                                item(key = "results_header") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SectionHeader(
                                            title = "Výsledky vyhledávání",
                                            badge = "${displayedResults.size} / ${state.searchResults.size} položek"
                                        )
                                        PlatformFilterRow(
                                            selected = state.resultPlatformFilter,
                                            totalCount = state.searchResults.size,
                                            ytCount = ytCount,
                                            ytmCount = ytmCount,
                                            otherCount = otherCount,
                                            onSelect = viewModel::selectResultPlatformFilter
                                        )
                                    }
                                }

                                if (displayedResults.isEmpty()) {
                                    item {
                                        EmptyStateCard("Pro zvolenou platformu nebyly v těchto výsledcích nalezeny položky.")
                                    }
                                } else {
                                    items(displayedResults, key = { "${it.platform.name}_${it.id}_${it.url}" }) { item ->
                                        MediaSearchCard(
                                            item = item,
                                            isFavorite = viewModel.isFavorite(item),
                                            onPlayVideo = { viewModel.playMedia(item, StreamMode.FAST_VIDEO) },
                                            onPlayAudio = { viewModel.playMedia(item, StreamMode.AUDIO_ONLY) },
                                            onAddQueue = { viewModel.addToQueue(item) },
                                            onToggleFav = { viewModel.toggleFavorite(item) },
                                            onShare = { viewModel.openShareModal(item) },
                                            onDownload = { viewModel.openDownloadModal(item) }
                                        )
                                    }
                                }
                                if (state.canLoadMore) {
                                    item {
                                        Button(
                                            onClick = { viewModel.search(queryOverride = state.lastSearchQuery, append = true) },
                                            enabled = !state.isLoadingMore,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.primaryBlue,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                if (state.isLoadingMore) "⏳ Načítám dalších 20..." else "➕ Načíst dalších 20 výsledků",
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        MainTab.QUEUE -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SectionHeader("Fronta přehrávání", "${state.queue.size}")
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilledTonalButton(
                                            onClick = viewModel::playNextInQueue,
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = colors.bgElevated,
                                                contentColor = colors.textPrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Další", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = viewModel::clearQueue,
                                            border = BorderStroke(1.dp, colors.border),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("🗑 Vyčistit", fontSize = 12.sp, color = colors.textSecondary)
                                        }
                                    }
                                }
                            }
                            if (state.queue.isEmpty()) {
                                item { EmptyStateCard("Fronta přehrávání je prázdná.") }
                            } else {
                                itemsIndexed(state.queue) { idx, item ->
                                    SideListMediaCard(
                                        item = item,
                                        isPlaying = state.currentMedia?.url == item.url,
                                        isFavorite = viewModel.isFavorite(item),
                                        subtitleExtra = null,
                                        onPlayVideo = { viewModel.playMedia(item) },
                                        onPlayAudio = { viewModel.playMedia(item, StreamMode.AUDIO_ONLY) },
                                        onAddQueue = null,
                                        onToggleFav = { viewModel.toggleFavorite(item) },
                                        onShare = { viewModel.openShareModal(item) },
                                        onDownload = { viewModel.openDownloadModal(item) },
                                        onRemove = { viewModel.removeFromQueue(idx) }
                                    )
                                }
                            }
                        }

                        MainTab.FAVORITES -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SectionHeader("Oblíbená videa", "${state.favorites.size}")
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = viewModel::playAllFavorites,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.primaryBlue,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("▶ Přehrát vše", fontSize = 12.sp, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = viewModel::clearFavorites,
                                            border = BorderStroke(1.dp, colors.border),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("🗑", fontSize = 12.sp, color = colors.textSecondary)
                                        }
                                    }
                                }
                            }
                            if (state.favorites.isEmpty()) {
                                item { EmptyStateCard("Zatím nemáte žádná oblíbená videa. Klepněte na ☆ u videa.") }
                            } else {
                                itemsIndexed(state.favorites) { _, item ->
                                    SideListMediaCard(
                                        item = item,
                                        isPlaying = state.currentMedia?.url == item.url,
                                        isFavorite = true,
                                        subtitleExtra = null,
                                        onPlayVideo = { viewModel.playMedia(item, StreamMode.FAST_VIDEO) },
                                        onPlayAudio = { viewModel.playMedia(item, StreamMode.AUDIO_ONLY) },
                                        onAddQueue = { viewModel.addToQueue(item) },
                                        onToggleFav = { viewModel.toggleFavorite(item) },
                                        onShare = { viewModel.openShareModal(item) },
                                        onDownload = { viewModel.openDownloadModal(item) },
                                        onRemove = { viewModel.toggleFavorite(item) }
                                    )
                                }
                            }
                        }

                        MainTab.HISTORY -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SectionHeader("Historie přehrávání", "${state.history.size}")
                                    OutlinedButton(
                                        onClick = viewModel::clearHistory,
                                        border = BorderStroke(1.dp, colors.border),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("🗑 Smazat historii", fontSize = 12.sp, color = colors.textSecondary)
                                    }
                                }
                            }
                            if (state.history.isEmpty()) {
                                item { EmptyStateCard("Historie přehrávání je zatím prázdná.") }
                            } else {
                                itemsIndexed(state.history) { idx, item ->
                                    val isCurrent = if (item.isLocal) {
                                        state.currentMedia?.localPath == item.localPath
                                    } else {
                                        state.currentMedia?.url == item.url
                                    }
                                    SideListMediaCard(
                                        item = item,
                                        isPlaying = isCurrent,
                                        isFavorite = viewModel.isFavorite(item),
                                        subtitleExtra = formatPlayedAt(item.playedAt),
                                        onPlayVideo = { viewModel.playMedia(item, if (item.isLocal) null else StreamMode.FAST_VIDEO) },
                                        onPlayAudio = if (item.isLocal) null else {
                                            { viewModel.playMedia(item, StreamMode.AUDIO_ONLY) }
                                        },
                                        onAddQueue = if (item.isLocal) null else {
                                            { viewModel.addToQueue(item) }
                                        },
                                        onToggleFav = if (item.isLocal) null else {
                                            { viewModel.toggleFavorite(item) }
                                        },
                                        onShare = { viewModel.openShareModal(item) },
                                        onDownload = if (item.isLocal) null else {
                                            { viewModel.openDownloadModal(item) }
                                        },
                                        onRemove = { viewModel.removeFromHistory(idx) }
                                    )
                                }
                            }
                        }

                        MainTab.DOWNLOADS -> {
                            item {
                                SectionHeader("Stahování a FFmpeg konverze", "${state.downloads.size}")
                            }
                            if (state.downloads.isEmpty()) {
                                item { EmptyStateCard("Zatím neprobíhá žádné stahování ani FFmpeg konverze.") }
                            } else {
                                items(state.downloads, key = { it.id }) { job ->
                                    DownloadJobCard(
                                        job = job,
                                        onPlay = {
                                            job.outputFilePath?.let { path ->
                                                viewModel.playMedia(
                                                    MediaItem(
                                                        id = path,
                                                        title = job.outputFileName ?: job.title,
                                                        url = "",
                                                        isLocal = true,
                                                        localPath = path,
                                                        ext = job.format.name
                                                    )
                                                )
                                            }
                                        },
                                        onCancelOrRemove = { viewModel.cancelOrRemoveDownload(job.id) }
                                    )
                                }
                            }
                        }

                        MainTab.LIBRARY -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SectionHeader("Stažené soubory", "${state.libraryFiles.size}")
                                    OutlinedButton(
                                        onClick = viewModel::refreshLibrary,
                                        border = BorderStroke(1.dp, colors.border),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Obnovit", fontSize = 12.sp, color = colors.textPrimary)
                                    }
                                }
                            }
                            if (state.libraryFiles.isEmpty()) {
                                item { EmptyStateCard("Ve složce Downloads/FFMPEGplaydownloader zatím nic není.") }
                            } else {
                                items(state.libraryFiles, key = { it.path }) { file ->
                                    LibraryFileCard(
                                        file = file,
                                        onPlay = { viewModel.playLocalFile(file) },
                                        onOpenFfmpeg = { viewModel.openLocalFfmpegDialog(file) },
                                        onShare = {
                                            viewModel.openShareModal(
                                                MediaItem(
                                                    id = file.path,
                                                    title = file.name,
                                                    url = "",
                                                    extractor = "Lokální",
                                                    platform = MediaPlatform.LOCAL,
                                                    isLocal = true,
                                                    localPath = file.path,
                                                    ext = file.ext
                                                )
                                            )
                                        },
                                        onDelete = { viewModel.deleteLocalFile(file) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
            }
        }

        // Studio Equalizer Modal (works in both portrait and landscape fullscreen)
        if (state.showEqualizerDialog && !state.isInPipMode) {
            EqualizerDialog(
                state = state.equalizerState,
                onDismiss = viewModel::closeEqualizer,
                onToggleEnabled = viewModel::setEqualizerEnabled,
                onSelectPreset = viewModel::selectEqualizerPreset,
                onBandLevelChange = viewModel::updateEqualizerBand,
                onBassBoostChange = viewModel::updateBassBoost,
                onLoudnessToggle = viewModel::setLoudnessEnabled,
                onReset = viewModel::resetEqualizer
            )
        }

        // Sleep Timer, Playback Speed, Repeat & Shuffle Modal
        if (state.showSleepTimerDialog && !state.isInPipMode) {
            SleepTimerAndSpeedDialog(
                playbackSpeed = state.playbackExtras.playbackSpeed,
                repeatMode = state.playbackExtras.repeatMode,
                shuffleEnabled = state.playbackExtras.shuffleEnabled,
                sleepTimerPreset = state.playbackExtras.sleepTimerPreset,
                sleepTimerRemainingSec = state.playbackExtras.sleepTimerRemainingSec,
                onSelectSpeed = viewModel::setPlaybackSpeed,
                onSelectRepeatMode = viewModel::setRepeatMode,
                onToggleShuffle = viewModel::setShuffleEnabled,
                onSelectSleepTimer = viewModel::selectSleepTimer,
                onDismiss = viewModel::closeSleepTimerDialog
            )
        }

        // Download / FFmpeg Conversion Modal
        if (!state.isInPipMode) {
            state.downloadModalTarget?.let { targetItem ->
                DownloadDialog(
                    item = targetItem,
                    onDismiss = viewModel::closeDownloadModal,
                    onStartDownload = { fmt, start, end, embedSubs, loudnorm ->
                        viewModel.startDownload(targetItem, fmt, start, end, embedSubs, loudnorm)
                    }
                )
            }
        }

        // Share, Broadcast, Chromecast & Mirroring Modal
        if (!state.isInPipMode) {
            state.shareModalTarget?.let { targetItem ->
                ShareAndCastDialog(
                    item = targetItem,
                    resolvedStreamUrl = state.shareResolvedVideoUrl,
                    resolvedAudioUrl = state.shareResolvedAudioUrl,
                    isResolvingStream = state.isResolvingShareStream,
                    onDismiss = viewModel::closeShareModal,
                    onRequestResolveStream = { viewModel.resolveShareStreams(targetItem) },
                    onShowStatus = viewModel::showStatusBanner
                )
            }
        }

        // Local FFmpeg Conversion Modal for Library files
        if (!state.isInPipMode) {
            state.localFfmpegJob.targetFile?.let { targetFile ->
                LocalFfmpegDialog(
                    file = targetFile,
                    isProcessing = state.localFfmpegJob.isProcessing,
                    progress = state.localFfmpegJob.progress,
                    statusMessage = state.localFfmpegJob.statusMessage,
                    onDismiss = viewModel::closeLocalFfmpegDialog,
                    onStartProcessing = { action, trimStart, trimEnd ->
                        viewModel.startLocalFfmpegProcessing(targetFile, action, trimStart, trimEnd)
                    }
                )
            }
        }

        // Inline Voice Search Listening Overlay Dialog
        if (state.isVoiceListening && !state.isInPipMode) {
            VoiceSearchDialog(
                partialText = state.voicePartialText,
                rmsNormalized = state.voiceRmsNormalized,
                onStopListening = viewModel::stopVoiceListening,
                onSelectHint = viewModel::selectVoiceHint,
                onSystemFallback = launchSystemVoiceFallback,
                onCancel = viewModel::cancelVoiceListening
            )
        }

        // Optional yt-dlp Cookies Settings Dialog
        if (state.showCookiesDialog && !state.isInPipMode) {
            CookiesSettingsDialog(
                status = state.cookiesStatus,
                feedbackMessage = state.cookiesFeedback,
                onImportUri = viewModel::importCookiesUri,
                onToggleEnabled = viewModel::setCookiesEnabled,
                onClearCookies = viewModel::clearCookies,
                onDismiss = viewModel::closeCookiesDialog
            )
        }

        // In-App README / Help Dialog
        if (state.showReadmeDialog && !state.isInPipMode) {
            ReadmeDialog(
                onDismiss = viewModel::closeReadmeDialog
            )
        }

        // Graceful App Exit Confirmation Dialog
        if (state.showExitConfirmDialog && !state.isInPipMode) {
            val activeFfmpegCount = if (state.localFfmpegJob.isProcessing) 1 else 0
            val activeTasksSummary = buildList {
                if (activeDownloadCount > 0) add("stahování: $activeDownloadCount")
                if (activeFfmpegCount > 0) add("FFmpeg zpracování: $activeFfmpegCount")
            }.joinToString(", ")

            AlertDialog(
                onDismissRequest = viewModel::dismissExitDialog,
                containerColor = colors.bgSurface,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textSecondary,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = colors.accentRed
                    )
                },
                title = {
                    Text(
                        text = "Ukončit aplikaci?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Zastaví přehrávání, ukončí službu na pozadí a uloží aktuální pozici.",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                        if (activeTasksSummary.isNotEmpty()) {
                            Text(
                                text = "⚠️ Aktivní úlohy budou zrušeny ($activeTasksSummary).",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.warningAmber
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::confirmExitApp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Ukončit aplikaci", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissExitDialog) {
                        Text("Zrušit", fontSize = 12.sp, color = colors.textPrimary)
                    }
                }
            )
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopStudioHeader(
    ytdlpVersion: String,
    ffmpegVersion: String,
    isUpdating: Boolean,
    cookiesStatus: CookiesStatus,
    onUpdateClick: () -> Unit,
    onCookiesClick: () -> Unit,
    onReadmeClick: () -> Unit,
    onBuyMeACoffeeClick: () -> Unit,
    onExitClick: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgSurface)
            .border(1.dp, colors.border)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(colors.accentRed, colors.accentOrange)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "FFMPEGplaydownloader",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Přehrávač & vyhledávač s FFmpeg a yt-dlp",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onUpdateClick,
                    enabled = !isUpdating,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.bgElevated,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text(
                        text = if (isUpdating) "⏳ Aktualizuji..." else "🔄 Aktualizovat",
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = onExitClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .border(
                            1.dp,
                            colors.accentRed.copy(alpha = 0.45f),
                            RoundedCornerShape(50)
                        ),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.accentRed.copy(alpha = if (colors.isDark) 0.14f else 0.10f),
                        contentColor = colors.accentRed
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Ukončit aplikaci",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "Ukončit",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        val cookiesDotColor = when {
            cookiesStatus.hasFile && cookiesStatus.enabled -> colors.successGreen
            cookiesStatus.hasFile && !cookiesStatus.enabled -> colors.warningAmber
            else -> colors.textSecondary
        }
        val cookiesLabel = when {
            cookiesStatus.hasFile && cookiesStatus.enabled -> "🍪 Cookies: Aktivní"
            cookiesStatus.hasFile && !cookiesStatus.enabled -> "🍪 Cookies: Pozastaveno"
            else -> "🍪 Cookies"
        }
        val cookiesBorderColor = when {
            cookiesStatus.hasFile && cookiesStatus.enabled -> colors.successGreen.copy(alpha = 0.55f)
            cookiesStatus.hasFile && !cookiesStatus.enabled -> colors.warningAmber.copy(alpha = 0.55f)
            else -> colors.border
        }
        val cookiesLabelColor = when {
            cookiesStatus.hasFile && cookiesStatus.enabled -> colors.successGreen
            cookiesStatus.hasFile && !cookiesStatus.enabled -> colors.warningAmber
            else -> colors.textPrimary
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(
                dotColor = colors.successGreen,
                label = "FFmpeg: $ffmpegVersion"
            )
            StatusPill(
                dotColor = if (isUpdating) colors.warningAmber else colors.successGreen,
                label = "yt-dlp: $ytdlpVersion"
            )
            StatusPill(
                dotColor = cookiesDotColor,
                label = cookiesLabel,
                borderColor = cookiesBorderColor,
                labelColor = cookiesLabelColor,
                onClick = onCookiesClick
            )
            StatusPill(
                dotColor = colors.primaryBlue,
                label = "📖 README",
                borderColor = colors.primaryBlue.copy(alpha = 0.55f),
                labelColor = colors.textPrimary,
                onClick = onReadmeClick
            )
            BuyMeACoffeePill(onClick = onBuyMeACoffeeClick)
        }
    }
}

@Composable
private fun BuyMeACoffeePill(
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    val coffeeContentColor = if (colors.isDark) colors.goldStar else Color(0xFF92400E)
    val coffeeGradient = if (colors.isDark) {
        listOf(
            Color(0xFF3A2A10),
            colors.goldStar.copy(alpha = 0.16f)
        )
    } else {
        listOf(
            Color(0xFFFEF3C7),
            colors.goldStar.copy(alpha = 0.14f)
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(coffeeGradient))
            .border(1.dp, colors.goldStar.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LocalCafe,
            contentDescription = "Buy Me a Coffee",
            tint = coffeeContentColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "☕ Buy Me a Coffee",
            color = coffeeContentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun StatusPill(
    dotColor: Color,
    label: String,
    borderColor: Color? = null,
    labelColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = AppTheme.colors
    val resolvedBorder = borderColor ?: colors.border
    val resolvedLabel = labelColor ?: colors.textSecondary
    val baseModifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(colors.bgElevated)
        .border(1.dp, resolvedBorder, RoundedCornerShape(50))
    val pillModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = pillModifier.padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, color = resolvedLabel, fontSize = 10.sp)
    }
}

@Composable
private fun BottomNavigationBar(
    state: MainUiState,
    activeDownloadCount: Int,
    onSelectTab: (MainTab) -> Unit
) {
    val colors = AppTheme.colors
    NavigationBar(
        containerColor = colors.bgSurface,
        contentColor = colors.textPrimary
    ) {
        MainTab.entries.forEach { tab ->
            val icon = when (tab) {
                MainTab.SEARCH -> Icons.Default.Search
                MainTab.QUEUE -> Icons.Default.QueueMusic
                MainTab.FAVORITES -> Icons.Default.Star
                MainTab.HISTORY -> Icons.Default.History
                MainTab.DOWNLOADS -> Icons.Default.Download
                MainTab.LIBRARY -> Icons.Default.Folder
            }
            val badgeText = when (tab) {
                MainTab.QUEUE -> if (state.queue.isNotEmpty()) "${state.queue.size}" else null
                MainTab.FAVORITES -> if (state.favorites.isNotEmpty()) "${state.favorites.size}" else null
                MainTab.HISTORY -> if (state.history.isNotEmpty()) "${state.history.size}" else null
                MainTab.DOWNLOADS -> if (activeDownloadCount > 0) "$activeDownloadCount" else null
                MainTab.LIBRARY -> if (state.libraryFiles.isNotEmpty()) "${state.libraryFiles.size}" else null
                MainTab.SEARCH -> null
            }

            NavigationBarItem(
                selected = state.selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(icon, contentDescription = tab.title, modifier = Modifier.size(20.dp))
                },
                label = {
                    Text(
                        text = if (badgeText != null) "${tab.title} ($badgeText)" else tab.title,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primaryBlue,
                    selectedTextColor = colors.primaryBlue,
                    unselectedIconColor = colors.textSecondary,
                    unselectedTextColor = colors.textSecondary,
                    indicatorColor = colors.primaryBlue.copy(alpha = if (colors.isDark) 0.14f else 0.12f)
                )
            )
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaSearchCard(
    item: MediaItem,
    isFavorite: Boolean,
    onPlayVideo: () -> Unit,
    onPlayAudio: () -> Unit,
    onAddQueue: () -> Unit,
    onToggleFav: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = AppTheme.colors
    val isYtMusic = item.platform == MediaPlatform.YOUTUBE_MUSIC
    val accentBorder = if (isYtMusic) {
        colors.youTubeMusicPink.copy(alpha = 0.55f)
    } else {
        colors.border
    }
    val defaultTapAction = if (isYtMusic && !item.contentKind.equals("Video", ignoreCase = true)) {
        onPlayAudio
    } else {
        onPlayVideo
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgElevated)
            .border(1.dp, accentBorder, RoundedCornerShape(10.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(if (colors.isDark) Color(0xFF080B11) else Color(0xFF1E293B))
                .clickable(onClick = defaultTapAction)
        ) {
            if (item.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            PlatformBadge(
                platform = item.platform,
                extractor = item.extractor,
                contentKind = item.contentKind,
                solid = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
            IconButton(
                onClick = onToggleFav,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Oblíbené",
                    tint = if (isFavorite) Color(0xFFFBBF24) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (item.durationString.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(item.durationString, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                item.title,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            val viewsStr = formatViews(item.viewCount, isMusic = isYtMusic)
            val meta = listOfNotNull(
                item.channel.takeIf { it.isNotBlank() },
                viewsStr.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            Text(meta, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isYtMusic) {
                    Button(
                        onClick = onPlayAudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.youTubeMusicPink,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Poslech", fontSize = 11.sp, color = Color.White)
                    }
                    FilledTonalButton(
                        onClick = onPlayVideo,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.bgSurface,
                            contentColor = colors.textPrimary
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Video", fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = onPlayVideo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryBlue,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Přehrát", fontSize = 11.sp, color = Color.White)
                    }
                    FilledTonalButton(
                        onClick = onPlayAudio,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.bgSurface,
                            contentColor = colors.textPrimary
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Zvuk", fontSize = 11.sp)
                    }
                }
                FilledTonalButton(
                    onClick = onAddQueue,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.bgSurface,
                        contentColor = colors.textPrimary
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("➕ Fronta", fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = onShare,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.bgSurface,
                        contentColor = colors.textPrimary
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sdílet", fontSize = 11.sp)
                }
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stáhnout", fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideListMediaCard(
    item: MediaItem,
    isPlaying: Boolean,
    isFavorite: Boolean,
    subtitleExtra: String?,
    onPlayVideo: () -> Unit,
    onPlayAudio: (() -> Unit)?,
    onAddQueue: (() -> Unit)?,
    onToggleFav: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onRemove: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isPlaying) colors.primaryBlue.copy(alpha = if (colors.isDark) 0.14f else 0.10f)
                else colors.bgElevated
            )
            .border(1.dp, if (isPlaying) colors.primaryBlue else colors.border, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PlatformBadge(
                platform = if (item.isLocal) MediaPlatform.LOCAL else item.platform,
                extractor = item.extractor,
                contentKind = item.contentKind
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Odebrat", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = (if (isPlaying) "▶ " else "") + item.title,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        val sub = listOfNotNull(
            if (item.isLocal) "📁 Lokální (${item.ext})" else item.channel.takeIf { it.isNotBlank() },
            item.durationString.takeIf { it.isNotBlank() },
            subtitleExtra?.takeIf { it.isNotBlank() }
        ).joinToString(" • ")

        if (sub.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(sub, color = colors.textSecondary, fontSize = 11.sp)
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onPlayVideo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("▶ Přehrát", fontSize = 11.sp, color = Color.White)
            }
            if (onPlayAudio != null) {
                OutlinedButton(
                    onClick = onPlayAudio,
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("🎵 Zvuk", fontSize = 11.sp, color = colors.textPrimary)
                }
            }
            if (onAddQueue != null) {
                OutlinedButton(
                    onClick = onAddQueue,
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("➕ Fronta", fontSize = 11.sp, color = colors.textPrimary)
                }
            }
            if (onToggleFav != null) {
                OutlinedButton(
                    onClick = onToggleFav,
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        if (isFavorite) "⭐" else "☆",
                        fontSize = 12.sp,
                        color = if (isFavorite) colors.goldStar else colors.textPrimary
                    )
                }
            }
            if (onShare != null) {
                OutlinedButton(
                    onClick = onShare,
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Sdílet", fontSize = 11.sp, color = colors.textPrimary)
                }
            }
            if (onDownload != null) {
                OutlinedButton(
                    onClick = onDownload,
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("⬇ Stáhnout", fontSize = 11.sp, color = colors.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun DownloadJobCard(
    job: DownloadJob,
    onPlay: () -> Unit,
    onCancelOrRemove: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgElevated)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                job.title,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancelOrRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Zrušit/Smazat", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }

        Text(
            "Formát: ${job.format.id.uppercase()}${if (job.loudnorm) " • Loudnorm" else ""}",
            color = colors.textSecondary,
            fontSize = 11.sp
        )

        LinearProgressIndicator(
            progress = { (job.progress / 100f).coerceIn(0f, 1f) },
            color = if (job.status == DownloadStatus.ERROR) colors.accentRed else colors.successGreen,
            trackColor = colors.bgHover,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
        )

        val etaPart = if (job.etaSeconds > 0) " (ETA ${job.etaSeconds}s)" else ""
        Text("${job.message}$etaPart", color = colors.textSecondary, fontSize = 11.sp)

        if (job.status == DownloadStatus.COMPLETED && job.outputFilePath != null) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("▶ Přehrát stažený soubor", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFileCard(
    file: LocalMediaFile,
    onPlay: () -> Unit,
    onOpenFfmpeg: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgElevated)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${if (file.isAudio) "🎵" else "🎬"} ${file.name}",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("${file.ext} • ${formatBytes(file.sizeBytes)}", color = colors.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("▶ Přehrát", fontSize = 11.sp, color = Color.White)
            }
            OutlinedButton(
                onClick = onOpenFfmpeg,
                border = BorderStroke(1.dp, colors.border),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("⚙ FFmpeg", fontSize = 11.sp, color = colors.textPrimary)
            }
            OutlinedButton(
                onClick = onShare,
                border = BorderStroke(1.dp, colors.border),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textPrimary)
                Spacer(Modifier.width(4.dp))
                Text("Sdílet", fontSize = 11.sp, color = colors.textPrimary)
            }
        }
    }
}

@Composable
private fun SearchOptionsStrip(
    selectedSource: SearchSourceFilter,
    smartEnabled: Boolean,
    onSelectSource: (SearchSourceFilter) -> Unit,
    onToggleSmart: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchSourceFilter.entries.forEach { source ->
            val selected = selectedSource == source
            val accent = when (source) {
                SearchSourceFilter.YOUTUBE_MUSIC -> colors.youTubeMusicPink
                SearchSourceFilter.YOUTUBE -> colors.youTubeRed
                SearchSourceFilter.ALL -> colors.primaryBlue
            }
            FilterChip(
                selected = selected,
                onClick = { onSelectSource(source) },
                label = {
                    Text(source.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.bgSurface,
                    labelColor = colors.textSecondary,
                    selectedContainerColor = accent.copy(alpha = if (colors.isDark) 0.22f else 0.14f),
                    selectedLabelColor = colors.textPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = colors.border,
                    selectedBorderColor = accent
                ),
                modifier = Modifier.height(30.dp)
            )
        }

        FilterChip(
            selected = smartEnabled,
            onClick = onToggleSmart,
            leadingIcon = {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (smartEnabled) colors.successGreen else colors.textSecondary
                )
            },
            label = {
                Text(
                    if (smartEnabled) "Chytré: ZAP" else "Chytré: VYP",
                    fontSize = 11.sp,
                    fontWeight = if (smartEnabled) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = colors.bgSurface,
                labelColor = colors.textSecondary,
                selectedContainerColor = colors.successGreen.copy(alpha = if (colors.isDark) 0.16f else 0.12f),
                selectedLabelColor = colors.successGreen
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = smartEnabled,
                borderColor = colors.border,
                selectedBorderColor = colors.successGreen
            ),
            modifier = Modifier.height(30.dp)
        )
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartSearchPlanCard(
    plan: SearchPlan,
    onSearchLiteral: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.primaryBlue.copy(alpha = if (colors.isDark) 0.10f else 0.08f))
            .border(1.dp, colors.primaryBlue.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = colors.primaryBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Inteligentní analýza dotazu",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(
                onClick = onSearchLiteral,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text("Hledat doslova", fontSize = 11.sp, color = colors.textSecondary)
            }
        }
        if (plan.explanation.isNotBlank()) {
            Text(plan.explanation, color = colors.textSecondary, fontSize = 11.sp)
        }
        if (plan.intentBadges.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                plan.intentBadges.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.bgElevated)
                            .border(1.dp, colors.border, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(badge, color = colors.textPrimary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformFilterRow(
    selected: ResultPlatformFilter,
    totalCount: Int,
    ytCount: Int,
    ytmCount: Int,
    otherCount: Int,
    onSelect: (ResultPlatformFilter) -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val options = buildList {
            add(ResultPlatformFilter.ALL to "Vše ($totalCount)")
            if (ytmCount > 0 || selected == ResultPlatformFilter.YOUTUBE_MUSIC) {
                add(ResultPlatformFilter.YOUTUBE_MUSIC to "🎧 YT Music ($ytmCount)")
            }
            if (ytCount > 0 || selected == ResultPlatformFilter.YOUTUBE) {
                add(ResultPlatformFilter.YOUTUBE to "▶ YouTube ($ytCount)")
            }
            if (otherCount > 0 || selected == ResultPlatformFilter.OTHER) {
                add(ResultPlatformFilter.OTHER to "🌐 Web ($otherCount)")
            }
        }
        options.forEach { (filter, label) ->
            val isSelected = selected == filter
            val accent = when (filter) {
                ResultPlatformFilter.YOUTUBE_MUSIC -> colors.youTubeMusicPink
                ResultPlatformFilter.YOUTUBE -> colors.youTubeRed
                ResultPlatformFilter.OTHER -> colors.otherPlatformTeal
                ResultPlatformFilter.ALL -> colors.primaryBlue
            }
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = {
                    Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.bgSurface,
                    labelColor = colors.textSecondary,
                    selectedContainerColor = accent.copy(alpha = if (colors.isDark) 0.22f else 0.14f),
                    selectedLabelColor = colors.textPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = colors.border,
                    selectedBorderColor = accent
                ),
                modifier = Modifier.height(30.dp)
            )
        }
    }
}

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchQuickTagsCard(onSelectTag: (String) -> Unit) {
    val colors = AppTheme.colors
    val tags = listOf(
        "🎧 Klidná hudba bez slov" to "chci si pustit klidnou hudbu na soustředění bez slov",
        "💪 Mix do fitka" to "energický mix do fitka na youtube music",
        "🎸 Kabát na YT Music" to "najdi mi písničky od Kabát na youtube music",
        "🌌 Vesmír CZ titulky" to "dokument o vesmíru 4k s českými titulky"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgSurface)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Hledejte přirozeným jazykem nebo vložte odkaz z YouTube či YouTube Music:",
            color = colors.textSecondary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach { (label, query) ->
                FilledTonalButton(
                    onClick = { onSelectTag(query) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.bgElevated,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, badge: String) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.bgElevated)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(badge, color = colors.textSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LoadingCard(text: String) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgSurface)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = colors.primaryBlue)
        Spacer(Modifier.height(10.dp))
        Text(text, color = colors.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgSurface)
            .border(1.dp, colors.border, RoundedCornerShape(10.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = colors.textSecondary, fontSize = 13.sp)
    }
}

private fun formatViews(views: Long?, isMusic: Boolean = false): String {
    if (views == null || views <= 0L) return ""
    val noun = if (isMusic) "přehrání" else "zhlédnutí"
    return when {
        views >= 1_000_000L -> String.format(Locale("cs", "CZ"), "%.1fM %s", views / 1_000_000.0, noun)
        views >= 1_000L -> String.format(Locale("cs", "CZ"), "%.1fK %s", views / 1_000.0, noun)
        else -> "$views $noun"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024.0 && idx < units.lastIndex) {
        value /= 1024.0
        idx++
    }
    return String.format(Locale("cs", "CZ"), "%.1f %s", value, units[idx])
}

private fun formatPlayedAt(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = Calendar.getInstance()
    val isToday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val timeStr = SimpleDateFormat("HH:mm", Locale("cs", "CZ")).format(Date(epochMs))
    if (isToday) return "Dnes $timeStr"
    val dateStr = SimpleDateFormat("d. M.", Locale("cs", "CZ")).format(Date(epochMs))
    return "$dateStr $timeStr"
}

@OptIn(UnstableApi::class)
@Composable
private fun PipPlayerSurface(
    state: MainUiState,
    exoPlayer: ExoPlayer
) {
    val current = state.currentMedia
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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

        if (current != null && state.streamMode == StreamMode.AUDIO_ONLY && !current.isLocal) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0E17)),
                contentAlignment = Alignment.Center
            ) {
                if (current.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = current.thumbnail,
                        contentDescription = current.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("🎧", fontSize = 36.sp)
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

@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSearchDialog(
    partialText: String,
    rmsNormalized: Float,
    onStopListening: () -> Unit,
    onSelectHint: (String) -> Unit,
    onSystemFallback: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = AppTheme.colors
    val animatedRms by animateFloatAsState(
        targetValue = rmsNormalized.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "voiceRmsScale"
    )
    val hintExamples = remember {
        listOf(
            "Pusť rovnou klidný jazz",
            "Najdi písničky od Kabát na YT Music",
            "Dokument o vesmíru s českými titulky",
            "Energický mix do fitka"
        )
    }

    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bgSurface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hlasové vyhledávání",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.accentRed.copy(alpha = if (colors.isDark) 0.18f else 0.12f))
                        .border(1.dp, colors.accentRed.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "● Poslouchám (CZ)",
                        color = colors.accentRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clickable(onClick = onStopListening),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .graphicsLayer {
                            val ringScale = 1f + animatedRms * 0.45f
                            scaleX = ringScale
                            scaleY = ringScale
                        }
                        .clip(CircleShape)
                        .background(colors.primaryBlue.copy(alpha = 0.22f + animatedRms * 0.18f))
                )
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .graphicsLayer {
                            val micScale = 1f + animatedRms * 0.14f
                            scaleX = micScale
                            scaleY = micScale
                        }
                        .clip(CircleShape)
                        .background(colors.primaryBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (animatedRms > 0.15f) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = "Dokončit hlasový vstup",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Text(
                text = if (partialText.isNotBlank()) partialText else "Řekněte dotaz přirozeným jazykem...",
                color = if (partialText.isNotBlank()) colors.textPrimary else colors.textSecondary,
                fontSize = if (partialText.isNotBlank()) 15.sp else 13.sp,
                fontWeight = if (partialText.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Příklady příkazů (klepnutím vyzkoušejte):",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hintExamples.forEach { example ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.bgElevated)
                                .border(1.dp, colors.border, RoundedCornerShape(50))
                                .clickable { onSelectHint(example) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = example,
                                color = colors.textPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSystemFallback,
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        "Systémový mikrofon",
                        fontSize = 12.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.bgElevated,
                        contentColor = colors.textPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Zrušit", fontSize = 12.sp, color = colors.textPrimary, maxLines = 1)
                }
            }
        }
    }
}
