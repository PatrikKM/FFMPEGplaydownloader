package com.ffmpegplaydownloader.app.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ffmpegplaydownloader.app.data.CookiesRepository
import com.ffmpegplaydownloader.app.data.CookiesStatus
import com.ffmpegplaydownloader.app.data.LocalFfmpegAction
import com.ffmpegplaydownloader.app.data.LocalFfmpegProcessor
import com.ffmpegplaydownloader.app.data.NaturalLanguageSearchEngine
import com.ffmpegplaydownloader.app.data.PersistenceRepository
import com.ffmpegplaydownloader.app.data.SearchPlan
import com.ffmpegplaydownloader.app.data.YtDlpRepository
import com.ffmpegplaydownloader.app.model.AudioFilterMode
import com.ffmpegplaydownloader.app.model.DownloadFormat
import com.ffmpegplaydownloader.app.model.DownloadJob
import com.ffmpegplaydownloader.app.model.DownloadStatus
import com.ffmpegplaydownloader.app.model.EqualizerBand
import com.ffmpegplaydownloader.app.model.EqualizerPreset
import com.ffmpegplaydownloader.app.model.EqualizerState
import com.ffmpegplaydownloader.app.model.LocalFfmpegJobState
import com.ffmpegplaydownloader.app.model.LocalMediaFile
import com.ffmpegplaydownloader.app.model.MainTab
import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.model.PlaybackExtrasState
import com.ffmpegplaydownloader.app.model.ResolvedStreams
import com.ffmpegplaydownloader.app.model.ResultPlatformFilter
import com.ffmpegplaydownloader.app.model.SearchSourceFilter
import com.ffmpegplaydownloader.app.model.StreamMode
import com.ffmpegplaydownloader.app.model.SubtitleInfo
import com.ffmpegplaydownloader.app.player.MediaPlayerController
import com.ffmpegplaydownloader.app.player.RepeatModeOption
import com.ffmpegplaydownloader.app.player.ResumePositionStore
import com.ffmpegplaydownloader.app.player.SleepTimerPreset
import com.ffmpegplaydownloader.app.voice.VoiceSearchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs

data class MainUiState(
    val ytdlpVersion: String = "Zjišťuji...",
    val ffmpegVersion: String = "FFmpeg 5.1",
    val isUpdatingYtDlp: Boolean = false,
    val statusBanner: String? = null,
    val selectedTab: MainTab = MainTab.SEARCH,
    val searchQuery: String = "",
    val lastSearchQuery: String = "",
    val searchSourceFilter: SearchSourceFilter = SearchSourceFilter.ALL,
    val resultPlatformFilter: ResultPlatformFilter = ResultPlatformFilter.ALL,
    val smartSearchEnabled: Boolean = true,
    val activeSearchPlan: SearchPlan? = null,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val searchError: String? = null,
    val searchResults: List<MediaItem> = emptyList(),
    val isVoiceListening: Boolean = false,
    val voicePartialText: String = "",
    val voiceRmsNormalized: Float = 0f,
    val voiceError: String? = null,
    val pendingVoiceAutoPlay: Boolean = false,
    val currentMedia: MediaItem? = null,
    val currentStreams: ResolvedStreams? = null,
    val isResolvingStream: Boolean = false,
    val streamError: String? = null,
    val streamMode: StreamMode = StreamMode.FAST_VIDEO,
    val audioFilter: AudioFilterMode = AudioFilterMode.NONE,
    val equalizerState: EqualizerState = EqualizerState(),
    val showEqualizerDialog: Boolean = false,
    val subtitlesEnabled: Boolean = true,
    val currentSubInfo: SubtitleInfo = SubtitleInfo(),
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isFullscreen: Boolean = false,
    val fullscreenCropZoom: Boolean = false,
    val isInPipMode: Boolean = false,
    val playbackExtras: PlaybackExtrasState = PlaybackExtrasState(),
    val showSleepTimerDialog: Boolean = false,
    val queue: List<MediaItem> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val history: List<MediaItem> = emptyList(),
    val downloads: List<DownloadJob> = emptyList(),
    val libraryFiles: List<LocalMediaFile> = emptyList(),
    val downloadModalTarget: MediaItem? = null,
    val localFfmpegJob: LocalFfmpegJobState = LocalFfmpegJobState(),
    val cookiesStatus: CookiesStatus = CookiesStatus(),
    val showCookiesDialog: Boolean = false,
    val cookiesFeedback: String? = null,
    val showReadmeDialog: Boolean = false,
    val showExitConfirmDialog: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cookiesRepo = CookiesRepository(application)
    private val ytDlpRepo = YtDlpRepository(application, cookiesRepo)
    private val persistenceRepo = PersistenceRepository(application)
    private val resumePositionStore = ResumePositionStore(application)
    private val localFfmpegProcessor = LocalFfmpegProcessor(application, ytDlpRepo)

    private var sleepTimerJob: Job? = null
    private var localFfmpegCoroutineJob: Job? = null
    private var searchJob: Job? = null
    private var positionUpdaterJob: Job? = null
    private var isShuttingDown: Boolean = false
    private var lastSavedResumeKey: String? = null
    private var lastSavedResumePosMs: Long = 0L
    private var wasPlayingBeforeVoice: Boolean = false
    private var voiceSearchController: VoiceSearchController? = null

    private val _voiceFallbackEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val voiceFallbackEvents: SharedFlow<Unit> = _voiceFallbackEvents.asSharedFlow()

    private val _exitAppEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitAppEvents: SharedFlow<Unit> = _exitAppEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val playerController = MediaPlayerController(
        context = application,
        onPlaybackEnded = {
            viewModelScope.launch {
                handlePlaybackEnded()
            }
        },
        onHardwareEqualizerReady = { minMb, maxMb, hwBands ->
            _uiState.update { state ->
                val eq = state.equalizerState
                val mergedBands = hwBands.map { hw ->
                    val savedLevel = eq.bands.getOrNull(hw.index)?.levelMb ?: hw.levelMb
                    hw.copy(levelMb = savedLevel.coerceIn(minMb, maxMb))
                }
                state.copy(
                    equalizerState = eq.copy(
                        minLevelMb = minMb,
                        maxLevelMb = maxMb,
                        bands = mergedBands
                    )
                )
            }
        }
    )

    init {
        loadPersistedData()
        refreshLibrary()
        checkAndAutoUpdateYtDlp()
        startPositionUpdater()
    }

    private fun loadPersistedData() {
        val eq = persistenceRepo.loadEqualizerState()
        val filterMode = deriveAudioFilterMode(eq)
        _uiState.update {
            it.copy(
                queue = persistenceRepo.loadQueue(),
                favorites = persistenceRepo.loadFavorites(),
                history = persistenceRepo.loadHistory(),
                equalizerState = eq,
                audioFilter = filterMode,
                cookiesStatus = cookiesRepo.getStatus()
            )
        }
        playerController.applyEqualizerState(eq)
    }

    private fun startPositionUpdater() {
        positionUpdaterJob?.cancel()
        positionUpdaterJob = viewModelScope.launch {
            while (true) {
                val player = playerController.exoPlayer
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.let { if (it > 0) it else (_uiState.value.currentMedia?.durationSec ?: 0L) * 1000L }
                val totalDur = dur.coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        currentPositionMs = pos,
                        totalDurationMs = totalDur,
                        isPlaying = player.isPlaying
                    )
                }
                val current = _uiState.value.currentMedia
                if (current != null && player.isPlaying && !_uiState.value.isResolvingStream) {
                    val key = mediaKey(current)
                    if (key != lastSavedResumeKey || abs(pos - lastSavedResumePosMs) >= 3_000L) {
                        resumePositionStore.savePosition(key, pos, totalDur)
                        lastSavedResumeKey = key
                        lastSavedResumePosMs = pos
                    }
                }
                delay(500L)
            }
        }
    }

    private fun saveCurrentResumePosition() {
        val current = _uiState.value.currentMedia ?: return
        if (_uiState.value.isResolvingStream) return
        val player = playerController.exoPlayer
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.let { if (it > 0) it else current.durationSec * 1000L }.coerceAtLeast(0L)
        val key = mediaKey(current)
        resumePositionStore.savePosition(key, pos, dur)
        lastSavedResumeKey = key
        lastSavedResumePosMs = pos
    }

    fun checkAndAutoUpdateYtDlp() {
        if (_uiState.value.isUpdatingYtDlp) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingYtDlp = true,
                    ytdlpVersion = ytDlpRepo.getYtDlpVersion(),
                    statusBanner = "Kontroluji nejnovější verzi yt-dlp..."
                )
            }
            try {
                val (ver, msg) = ytDlpRepo.updateYtDlp()
                _uiState.update {
                    it.copy(
                        ytdlpVersion = ver,
                        isUpdatingYtDlp = false,
                        statusBanner = "✅ $msg"
                    )
                }
            } catch (e: Exception) {
                val ver = ytDlpRepo.getYtDlpVersion()
                _uiState.update {
                    it.copy(
                        ytdlpVersion = ver,
                        isUpdatingYtDlp = false,
                        statusBanner = "ℹ️ yt-dlp $ver (kontrola aktualizace: ${e.localizedMessage ?: "offline"})"
                    )
                }
            }
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    // --- Cookies (yt-dlp) ---
    fun openCookiesDialog() {
        _uiState.update {
            it.copy(
                showCookiesDialog = true,
                cookiesStatus = cookiesRepo.getStatus(),
                cookiesFeedback = null
            )
        }
    }

    fun closeCookiesDialog() {
        _uiState.update {
            it.copy(
                showCookiesDialog = false,
                cookiesFeedback = null
            )
        }
    }

    // --- In-App README / Help ---
    fun openReadmeDialog() {
        _uiState.update { it.copy(showReadmeDialog = true) }
    }

    fun closeReadmeDialog() {
        _uiState.update { it.copy(showReadmeDialog = false) }
    }

    fun importCookiesUri(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                cookiesRepo.importFromUri(uri)
            }
            result.onSuccess { status ->
                ytDlpRepo.clearStreamCache()
                _uiState.update {
                    it.copy(
                        cookiesStatus = status,
                        cookiesFeedback = "✅ Nahráno ${status.entryCount} cookies (${status.fileName})."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        cookiesStatus = cookiesRepo.getStatus(),
                        cookiesFeedback = "❌ ${err.localizedMessage ?: "Nepodařilo se načíst soubor cookies.txt."}"
                    )
                }
            }
        }
    }

    fun setCookiesEnabled(enabled: Boolean) {
        val updated = cookiesRepo.setEnabled(enabled)
        ytDlpRepo.clearStreamCache()
        _uiState.update {
            it.copy(
                cookiesStatus = updated,
                cookiesFeedback = if (updated.hasFile) {
                    if (updated.enabled) {
                        "✅ Cookies jsou aktivní pro přehrávání a stahování."
                    } else {
                        "⏸ Cookies jsou pozastaveny."
                    }
                } else {
                    null
                }
            )
        }
    }

    fun clearCookies() {
        val cleared = cookiesRepo.clearCookies()
        ytDlpRepo.clearStreamCache()
        _uiState.update {
            it.copy(
                cookiesStatus = cleared,
                cookiesFeedback = "🗑 Soubor cookies.txt byl odstraněn."
            )
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == MainTab.LIBRARY) {
            refreshLibrary()
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, pendingVoiceAutoPlay = false) }
    }

    fun updateSearchSourceFilter(filter: SearchSourceFilter) {
        _uiState.update { it.copy(searchSourceFilter = filter) }
        val state = _uiState.value
        if (state.lastSearchQuery.isNotBlank() && state.selectedTab == MainTab.SEARCH && !state.isSearching) {
            search(queryOverride = state.lastSearchQuery)
        }
    }

    fun toggleSmartSearch() {
        val next = !_uiState.value.smartSearchEnabled
        _uiState.update { it.copy(smartSearchEnabled = next) }
        val state = _uiState.value
        if (state.lastSearchQuery.isNotBlank() && state.selectedTab == MainTab.SEARCH && !state.isSearching) {
            search(queryOverride = state.lastSearchQuery)
        }
    }

    fun selectResultPlatformFilter(filter: ResultPlatformFilter) {
        _uiState.update { it.copy(resultPlatformFilter = filter) }
    }

    fun search(
        queryOverride: String? = null,
        append: Boolean = false,
        bypassSmartNlp: Boolean = false
    ) {
        val raw = (queryOverride ?: _uiState.value.searchQuery).trim()
        if (raw.isEmpty()) return

        val stateSnapshot = _uiState.value
        val plan = NaturalLanguageSearchEngine.analyze(
            rawQuery = raw,
            userSourceFilter = stateSnapshot.searchSourceFilter,
            smartEnabled = if (bypassSmartNlp) false else stateSnapshot.smartSearchEnabled
        )
        val shouldAutoPlayFirst = !append && stateSnapshot.pendingVoiceAutoPlay && plan.autoPlayFirstResult

        val currentResults = if (append) stateSnapshot.searchResults else emptyList()
        val offset = if (append) currentResults.size else 0

        if (!append) {
            searchJob?.cancel()
        }
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    searchQuery = raw,
                    lastSearchQuery = raw,
                    activeSearchPlan = plan,
                    resultPlatformFilter = if (append) it.resultPlatformFilter else ResultPlatformFilter.ALL,
                    selectedTab = MainTab.SEARCH,
                    isSearching = !append,
                    isLoadingMore = append,
                    searchError = null,
                    pendingVoiceAutoPlay = shouldAutoPlayFirst
                )
            }
            try {
                val newItems = ytDlpRepo.searchWithPlan(plan, limit = 20, offset = offset)
                val merged = if (append) {
                    val existingIds = currentResults.map { it.id }.toSet()
                    currentResults + newItems.filter { it.id !in existingIds }
                } else {
                    newItems
                }
                _uiState.update {
                    it.copy(
                        searchResults = merged,
                        isSearching = false,
                        isLoadingMore = false,
                        canLoadMore = !plan.isDirectUrl && newItems.isNotEmpty() && merged.size < 100,
                        pendingVoiceAutoPlay = false
                    )
                }
                if (shouldAutoPlayFirst && merged.isNotEmpty()) {
                    playMedia(merged.first())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        isLoadingMore = false,
                        pendingVoiceAutoPlay = false,
                        searchError = "Chyba vyhledávání: ${e.localizedMessage ?: "Neznámá chyba"}"
                    )
                }
            }
        }
    }

    // --- Voice Search ---
    private fun getOrCreateVoiceController(): VoiceSearchController {
        voiceSearchController?.let { return it }
        return VoiceSearchController(
            context = getApplication(),
            onReady = {
                _uiState.update { it.copy(voiceError = null) }
            },
            onRmsChanged = { rmsDb ->
                onVoiceRms(rmsDb)
            },
            onPartialResult = { partial ->
                onVoicePartial(partial)
            },
            onFinalResult = { spokenText ->
                onVoiceResult(spokenText)
            },
            onError = { message, shouldFallbackToSystemIntent ->
                if (shouldFallbackToSystemIntent) {
                    _uiState.update {
                        it.copy(
                            isVoiceListening = false,
                            voicePartialText = "",
                            voiceRmsNormalized = 0f,
                            voiceError = null
                        )
                    }
                    _voiceFallbackEvents.tryEmit(Unit)
                } else {
                    onVoiceError(message)
                }
            }
        ).also { voiceSearchController = it }
    }

    fun startVoiceListening() {
        onVoiceListeningStarted()
        getOrCreateVoiceController().startListening("cs-CZ")
    }

    fun stopVoiceListening() {
        voiceSearchController?.stopListening()
    }

    fun cancelVoiceListening() {
        voiceSearchController?.cancel()
        onVoiceCancelled()
    }

    fun cancelVoiceIfListening() {
        if (_uiState.value.isVoiceListening) {
            cancelVoiceListening()
        }
    }

    fun prepareForSystemVoiceFallback() {
        if (!wasPlayingBeforeVoice && playerController.exoPlayer.isPlaying) {
            saveCurrentResumePosition()
            playerController.exoPlayer.pause()
            wasPlayingBeforeVoice = true
        }
        voiceSearchController?.cancel()
        _uiState.update {
            it.copy(
                isVoiceListening = false,
                voicePartialText = "",
                voiceRmsNormalized = 0f,
                voiceError = null
            )
        }
    }

    fun selectVoiceHint(hintQuery: String) {
        voiceSearchController?.cancel()
        onVoiceResult(hintQuery)
    }

    fun dismissVoiceError() {
        _uiState.update { it.copy(voiceError = null) }
    }

    fun onVoiceListeningStarted() {
        if (!wasPlayingBeforeVoice && playerController.exoPlayer.isPlaying) {
            saveCurrentResumePosition()
            playerController.exoPlayer.pause()
            wasPlayingBeforeVoice = true
        }
        _uiState.update {
            it.copy(
                isVoiceListening = true,
                voicePartialText = "",
                voiceRmsNormalized = 0f,
                voiceError = null
            )
        }
    }

    fun onVoicePartial(text: String) {
        _uiState.update { it.copy(voicePartialText = text) }
    }

    fun onVoiceRms(rmsDb: Float) {
        val normalized = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        _uiState.update { it.copy(voiceRmsNormalized = normalized) }
    }

    fun onVoiceResult(spokenText: String) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) {
            onVoiceError("Nepodařilo se rozpoznat řeč. Zkuste to prosím znovu.")
            return
        }
        // Leave player ready for the new query instead of resuming previous playback.
        wasPlayingBeforeVoice = false
        val stateSnapshot = _uiState.value
        val previewPlan = NaturalLanguageSearchEngine.analyze(
            rawQuery = trimmed,
            userSourceFilter = stateSnapshot.searchSourceFilter,
            smartEnabled = stateSnapshot.smartSearchEnabled
        )
        _uiState.update {
            it.copy(
                isVoiceListening = false,
                voicePartialText = "",
                voiceRmsNormalized = 0f,
                voiceError = null,
                searchQuery = trimmed,
                pendingVoiceAutoPlay = previewPlan.autoPlayFirstResult
            )
        }
        search(queryOverride = trimmed)
    }

    fun onVoiceError(msg: String) {
        _uiState.update {
            it.copy(
                isVoiceListening = false,
                voicePartialText = "",
                voiceRmsNormalized = 0f,
                voiceError = msg,
                pendingVoiceAutoPlay = false
            )
        }
        if (wasPlayingBeforeVoice) {
            wasPlayingBeforeVoice = false
            playerController.exoPlayer.play()
        }
    }

    fun onVoiceCancelled() {
        _uiState.update {
            it.copy(
                isVoiceListening = false,
                voicePartialText = "",
                voiceRmsNormalized = 0f,
                pendingVoiceAutoPlay = false
            )
        }
        if (wasPlayingBeforeVoice) {
            wasPlayingBeforeVoice = false
            playerController.exoPlayer.play()
        }
    }

    fun playMedia(
        item: MediaItem,
        forceMode: StreamMode? = null,
        startPositionMs: Long? = null,
        recordHistory: Boolean = true,
        savePreviousPosition: Boolean = true
    ) {
        if (startPositionMs == null && savePreviousPosition) {
            saveCurrentResumePosition()
        }

        val itemKey = mediaKey(item)
        val effectiveStartPositionMs = startPositionMs ?: resumePositionStore.getResumePosition(itemKey)
        lastSavedResumeKey = itemKey
        lastSavedResumePosMs = effectiveStartPositionMs

        val targetMode = forceMode ?: when {
            item.platform == MediaPlatform.YOUTUBE_MUSIC && !item.contentKind.equals("Video", ignoreCase = true) ->
                StreamMode.AUDIO_ONLY
            else -> _uiState.value.streamMode
        }
        if (recordHistory) {
            addToHistory(item)
        }

        _uiState.update {
            it.copy(
                currentMedia = item,
                streamMode = targetMode,
                streamError = null,
                isResolvingStream = !item.isLocal
            )
        }

        if (item.isLocal) {
            _uiState.update {
                it.copy(
                    isResolvingStream = false,
                    currentStreams = null,
                    currentSubInfo = SubtitleInfo(available = false, label = "Lokální soubor")
                )
            }
            playerController.playLocalFile(item.localPath, item.title, effectiveStartPositionMs)
            playerController.setPlaybackSpeed(_uiState.value.playbackExtras.playbackSpeed)
            return
        }

        viewModelScope.launch {
            try {
                val streams = ytDlpRepo.resolveMediaStreams(item.url)
                if (_uiState.value.currentMedia?.url != item.url) return@launch

                val updatedItem = if (item.durationSec == 0L && streams.durationSec > 0L) {
                    item.copy(durationSec = streams.durationSec, durationString = streams.durationString)
                } else item

                _uiState.update {
                    it.copy(
                        currentMedia = updatedItem,
                        currentStreams = streams,
                        isResolvingStream = false,
                        currentSubInfo = streams.czechSubtitles
                    )
                }

                playerController.playOnlineStreams(
                    streams = streams,
                    streamMode = targetMode,
                    subtitlesEnabled = _uiState.value.subtitlesEnabled,
                    startPositionMs = effectiveStartPositionMs
                )
                playerController.setPlaybackSpeed(_uiState.value.playbackExtras.playbackSpeed)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        streamError = "Nelze načíst stream: ${e.localizedMessage ?: "Chyba"}"
                    )
                }
            }
        }
    }

    fun changeStreamMode(mode: StreamMode) {
        val current = _uiState.value.currentMedia
        val pos = playerController.exoPlayer.currentPosition.coerceAtLeast(0L)
        _uiState.update { it.copy(streamMode = mode) }
        if (current != null && !current.isLocal) {
            playMedia(current, forceMode = mode, startPositionMs = pos, recordHistory = false)
        }
    }

    fun changeAudioFilter(filter: AudioFilterMode) {
        val updatedEq = when (filter) {
            AudioFilterMode.NONE -> _uiState.value.equalizerState.copy(
                enabled = false,
                loudnessEnabled = false,
                bassBoostStrength = 0
            )
            AudioFilterMode.LOUDNESS -> _uiState.value.equalizerState.copy(
                loudnessEnabled = true
            )
            AudioFilterMode.BASS_BOOST -> applyPresetToEq(
                _uiState.value.equalizerState.copy(enabled = true, bassBoostStrength = 750),
                EqualizerPreset.BASS_BOOST
            )
        }
        commitEqualizerState(updatedEq, explicitFilterMode = filter)
    }

    fun openEqualizer() {
        _uiState.update { it.copy(showEqualizerDialog = true) }
    }

    fun closeEqualizer() {
        _uiState.update { it.copy(showEqualizerDialog = false) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        commitEqualizerState(_uiState.value.equalizerState.copy(enabled = enabled))
    }

    fun selectEqualizerPreset(preset: EqualizerPreset) {
        val base = _uiState.value.equalizerState.copy(
            enabled = if (preset == EqualizerPreset.FLAT) _uiState.value.equalizerState.enabled else true,
            bassBoostStrength = if (preset == EqualizerPreset.BASS_BOOST) 650 else _uiState.value.equalizerState.bassBoostStrength
        )
        commitEqualizerState(applyPresetToEq(base, preset))
    }

    fun updateEqualizerBand(bandIndex: Int, levelMb: Int) {
        val current = _uiState.value.equalizerState
        val clamped = levelMb.coerceIn(current.minLevelMb, current.maxLevelMb)
        val newBands = current.bands.mapIndexed { idx, band ->
            if (idx == bandIndex) band.copy(levelMb = clamped) else band
        }
        commitEqualizerState(
            current.copy(
                enabled = true,
                preset = EqualizerPreset.CUSTOM,
                bands = newBands
            )
        )
    }

    fun updateBassBoost(strength: Int) {
        val current = _uiState.value.equalizerState
        val clamped = strength.coerceIn(0, 1000)
        commitEqualizerState(
            current.copy(
                enabled = if (clamped > 0) true else current.enabled,
                bassBoostStrength = clamped
            )
        )
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        commitEqualizerState(_uiState.value.equalizerState.copy(loudnessEnabled = enabled))
    }

    fun resetEqualizer() {
        val current = _uiState.value.equalizerState
        val resetBands = current.bands.map { it.copy(levelMb = 0) }
        commitEqualizerState(
            current.copy(
                enabled = false,
                preset = EqualizerPreset.FLAT,
                bassBoostStrength = 0,
                loudnessEnabled = false,
                bands = resetBands
            ),
            explicitFilterMode = AudioFilterMode.NONE
        )
    }

    private fun applyPresetToEq(eq: EqualizerState, preset: EqualizerPreset): EqualizerState {
        if (preset == EqualizerPreset.CUSTOM) return eq.copy(preset = preset)
        val targets = preset.bandLevelsMb
        val newBands = eq.bands.mapIndexed { idx, band ->
            val targetMb = targets.getOrElse(idx) {
                val ratio = idx.toFloat() / (eq.bands.size - 1).coerceAtLeast(1)
                val mappedIdx = (ratio * (targets.size - 1)).toInt().coerceIn(0, targets.lastIndex)
                targets[mappedIdx]
            }
            band.copy(levelMb = targetMb.coerceIn(eq.minLevelMb, eq.maxLevelMb))
        }
        return eq.copy(preset = preset, bands = newBands)
    }

    private fun commitEqualizerState(
        newEq: EqualizerState,
        explicitFilterMode: AudioFilterMode? = null
    ) {
        persistenceRepo.saveEqualizerState(newEq)
        playerController.applyEqualizerState(newEq)
        val filterMode = explicitFilterMode ?: deriveAudioFilterMode(newEq)
        _uiState.update {
            it.copy(
                equalizerState = newEq,
                audioFilter = filterMode
            )
        }
    }

    private fun deriveAudioFilterMode(eq: EqualizerState): AudioFilterMode = when {
        eq.enabled && (eq.preset == EqualizerPreset.BASS_BOOST || eq.bassBoostStrength >= 500) -> AudioFilterMode.BASS_BOOST
        eq.loudnessEnabled -> AudioFilterMode.LOUDNESS
        else -> AudioFilterMode.NONE
    }

    fun toggleSubtitles() {
        val next = !_uiState.value.subtitlesEnabled
        _uiState.update { it.copy(subtitlesEnabled = next) }
        playerController.setSubtitlesEnabled(next)
    }

    fun seekToMs(ms: Long) {
        playerController.seekToMs(ms)
    }

    fun seekByMs(deltaMs: Long) {
        playerController.seekByMs(deltaMs)
    }

    fun togglePlayPause() {
        val player = playerController.exoPlayer
        if (player.isPlaying) {
            saveCurrentResumePosition()
            player.pause()
        } else {
            player.play()
        }
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun exitFullscreen() {
        _uiState.update { it.copy(isFullscreen = false) }
    }

    fun toggleFullscreenZoom() {
        _uiState.update { it.copy(fullscreenCropZoom = !it.fullscreenCropZoom) }
    }

    // --- Sleep Timer, Playback Speed, Repeat & Shuffle ---
    fun openSleepTimerDialog() {
        _uiState.update { it.copy(showSleepTimerDialog = true) }
    }

    fun closeSleepTimerDialog() {
        _uiState.update { it.copy(showSleepTimerDialog = false) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        playerController.setPlaybackSpeed(clamped)
        _uiState.update {
            it.copy(playbackExtras = it.playbackExtras.copy(playbackSpeed = clamped))
        }
    }

    fun setRepeatMode(option: RepeatModeOption) {
        playerController.setRepeatModeOption(option)
        _uiState.update { state ->
            val extras = state.playbackExtras
            val updatedTimerPreset = if (
                option == RepeatModeOption.ONE &&
                extras.sleepTimerPreset == SleepTimerPreset.END_OF_TRACK
            ) {
                SleepTimerPreset.OFF
            } else {
                extras.sleepTimerPreset
            }
            state.copy(
                playbackExtras = extras.copy(
                    repeatMode = option,
                    sleepTimerPreset = updatedTimerPreset,
                    sleepTimerRemainingSec = if (updatedTimerPreset == SleepTimerPreset.OFF) 0L else extras.sleepTimerRemainingSec
                )
            )
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(playbackExtras = it.playbackExtras.copy(shuffleEnabled = enabled))
        }
    }

    fun selectSleepTimer(preset: SleepTimerPreset) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        when {
            preset == SleepTimerPreset.OFF -> {
                _uiState.update {
                    it.copy(
                        playbackExtras = it.playbackExtras.copy(
                            sleepTimerPreset = SleepTimerPreset.OFF,
                            sleepTimerRemainingSec = 0L
                        )
                    )
                }
            }
            preset == SleepTimerPreset.END_OF_TRACK -> {
                val wasRepeatOne = _uiState.value.playbackExtras.repeatMode == RepeatModeOption.ONE
                if (wasRepeatOne) {
                    playerController.setRepeatModeOption(RepeatModeOption.OFF)
                }
                _uiState.update {
                    it.copy(
                        playbackExtras = it.playbackExtras.copy(
                            repeatMode = if (wasRepeatOne) RepeatModeOption.OFF else it.playbackExtras.repeatMode,
                            sleepTimerPreset = SleepTimerPreset.END_OF_TRACK,
                            sleepTimerRemainingSec = 0L
                        )
                    )
                }
            }
            preset.minutes > 0 -> {
                val totalSec = preset.minutes * 60L
                _uiState.update {
                    it.copy(
                        playbackExtras = it.playbackExtras.copy(
                            sleepTimerPreset = preset,
                            sleepTimerRemainingSec = totalSec
                        )
                    )
                }
                sleepTimerJob = viewModelScope.launch {
                    var remaining = totalSec
                    while (remaining > 0L) {
                        delay(1000L)
                        remaining--
                        _uiState.update {
                            it.copy(
                                playbackExtras = it.playbackExtras.copy(sleepTimerRemainingSec = remaining)
                            )
                        }
                    }
                    saveCurrentResumePosition()
                    playerController.exoPlayer.pause()
                    _uiState.update {
                        it.copy(
                            playbackExtras = it.playbackExtras.copy(
                                sleepTimerPreset = SleepTimerPreset.OFF,
                                sleepTimerRemainingSec = 0L
                            ),
                            statusBanner = "⏱ Časovač vypnutí: přehrávání pozastaveno"
                        )
                    }
                }
            }
        }
    }

    // --- Picture-in-Picture ---
    fun setPipMode(inPip: Boolean) {
        _uiState.update { it.copy(isInPipMode = inPip) }
    }

    fun canAutoEnterPip(): Boolean {
        val state = _uiState.value
        return state.currentMedia != null &&
            (state.isPlaying || playerController.exoPlayer.isPlaying) &&
            !state.isVoiceListening &&
            !state.showEqualizerDialog &&
            !state.showSleepTimerDialog &&
            !state.showCookiesDialog &&
            !state.showReadmeDialog &&
            !state.showExitConfirmDialog &&
            !isShuttingDown &&
            state.downloadModalTarget == null &&
            state.localFfmpegJob.targetFile == null
    }

    fun isVideoPipMode(): Boolean {
        val state = _uiState.value
        val current = state.currentMedia ?: return false
        if (current.isLocal) {
            val audioExts = setOf("mp3", "flac", "wav", "m4a", "opus", "ogg", "aac")
            return current.ext.lowercase() !in audioExts
        }
        return state.streamMode != StreamMode.AUDIO_ONLY
    }

    // --- Queue ---
    fun addToQueue(item: MediaItem) {
        val currentQueue = _uiState.value.queue
        if (currentQueue.none { mediaKey(it) == mediaKey(item) }) {
            val updated = currentQueue + item
            persistenceRepo.saveQueue(updated)
            _uiState.update { it.copy(queue = updated) }
        }
        if (_uiState.value.currentMedia == null) {
            playMedia(item)
        }
    }

    fun removeFromQueue(index: Int) {
        val list = _uiState.value.queue.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            persistenceRepo.saveQueue(list)
            _uiState.update { it.copy(queue = list) }
        }
    }

    fun clearQueue() {
        persistenceRepo.saveQueue(emptyList())
        _uiState.update { it.copy(queue = emptyList()) }
    }

    private fun handlePlaybackEnded() {
        _uiState.value.currentMedia?.let { finished ->
            resumePositionStore.clearPosition(mediaKey(finished))
            lastSavedResumeKey = null
            lastSavedResumePosMs = 0L
        }
        if (_uiState.value.playbackExtras.sleepTimerPreset == SleepTimerPreset.END_OF_TRACK) {
            playerController.exoPlayer.pause()
            _uiState.update {
                it.copy(
                    playbackExtras = it.playbackExtras.copy(
                        sleepTimerPreset = SleepTimerPreset.OFF,
                        sleepTimerRemainingSec = 0L
                    ),
                    statusBanner = "⏱ Časovač vypnutí: přehrávání zastaveno po skladbě"
                )
            }
            return
        }
        playNextInQueue(autoAdvanced = true)
    }

    fun playNextInQueue(autoAdvanced: Boolean = false) {
        val q = _uiState.value.queue
        if (q.isEmpty()) return
        val extras = _uiState.value.playbackExtras
        val cur = _uiState.value.currentMedia
        val currentIdx = if (cur != null) q.indexOfFirst { mediaKey(it) == mediaKey(cur) } else -1

        val nextIdx = if (extras.shuffleEnabled) {
            when {
                q.size == 1 -> {
                    if (!autoAdvanced || extras.repeatMode == RepeatModeOption.ALL) 0 else return
                }
                else -> {
                    val candidates = q.indices.filter { it != currentIdx }
                    candidates.random()
                }
            }
        } else {
            when {
                currentIdx == -1 -> 0
                currentIdx + 1 < q.size -> currentIdx + 1
                extras.repeatMode == RepeatModeOption.ALL -> 0
                else -> return
            }
        }

        val target = q[nextIdx]
        val startPos = if (autoAdvanced && cur != null && mediaKey(target) == mediaKey(cur)) 0L else null
        playMedia(target, startPositionMs = startPos, savePreviousPosition = !autoAdvanced)
    }

    // --- Favorites ---
    fun isFavorite(item: MediaItem?): Boolean {
        if (item == null || item.isLocal) return false
        return _uiState.value.favorites.any { it.url == item.url }
    }

    fun toggleFavorite(item: MediaItem) {
        if (item.isLocal || item.url.isBlank()) return
        val list = _uiState.value.favorites.toMutableList()
        val idx = list.indexOfFirst { it.url == item.url }
        if (idx != -1) {
            list.removeAt(idx)
        } else {
            list.add(0, item.copy(addedAt = System.currentTimeMillis()))
        }
        persistenceRepo.saveFavorites(list)
        _uiState.update { it.copy(favorites = list) }
    }

    fun playAllFavorites() {
        val favs = _uiState.value.favorites
        if (favs.isEmpty()) return
        val mergedQueue = (_uiState.value.queue + favs).distinctBy { mediaKey(it) }
        persistenceRepo.saveQueue(mergedQueue)
        _uiState.update { it.copy(queue = mergedQueue) }
        playMedia(favs.first())
    }

    fun clearFavorites() {
        persistenceRepo.saveFavorites(emptyList())
        _uiState.update { it.copy(favorites = emptyList()) }
    }

    // --- Playback History ---
    private fun addToHistory(item: MediaItem) {
        val key = mediaKey(item)
        if (key.isBlank()) return
        val filtered = _uiState.value.history.filterNot { mediaKey(it) == key }
        val updated = (listOf(item.copy(playedAt = System.currentTimeMillis())) + filtered).take(100)
        persistenceRepo.saveHistory(updated)
        _uiState.update { it.copy(history = updated) }
    }

    fun removeFromHistory(index: Int) {
        val list = _uiState.value.history.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            persistenceRepo.saveHistory(list)
            _uiState.update { it.copy(history = list) }
        }
    }

    fun clearHistory() {
        persistenceRepo.saveHistory(emptyList())
        _uiState.update { it.copy(history = emptyList()) }
    }

    // --- Downloads & FFmpeg ---
    fun openDownloadModal(item: MediaItem) {
        _uiState.update { it.copy(downloadModalTarget = item) }
    }

    fun closeDownloadModal() {
        _uiState.update { it.copy(downloadModalTarget = null) }
    }

    fun startDownload(
        item: MediaItem,
        format: DownloadFormat,
        trimStart: String,
        trimEnd: String,
        embedSubs: Boolean,
        loudnorm: Boolean
    ) {
        val jobId = UUID.randomUUID().toString().take(8)
        val newJob = DownloadJob(
            id = jobId,
            url = item.url,
            title = item.title,
            format = format,
            trimStart = trimStart,
            trimEnd = trimEnd,
            embedSubs = embedSubs,
            loudnorm = loudnorm,
            status = DownloadStatus.STARTING,
            message = "Spouštím yt-dlp..."
        )

        _uiState.update {
            it.copy(
                downloadModalTarget = null,
                selectedTab = MainTab.DOWNLOADS,
                downloads = listOf(newJob) + it.downloads
            )
        }

        viewModelScope.launch {
            try {
                val outFile = ytDlpRepo.executeDownload(
                    processId = jobId,
                    url = item.url,
                    format = format,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    embedSubs = embedSubs,
                    loudnorm = loudnorm
                ) { progress, eta, line ->
                    val isProcessing = line.contains("[Merger]") ||
                        line.contains("[ExtractAudio]") ||
                        line.contains("[EmbedThumbnail]") ||
                        line.contains("[EmbedSubtitle]") ||
                        line.contains("[Metadata]") ||
                        line.contains("Post-process")

                    updateDownloadJob(jobId) { job ->
                        if (job.status == DownloadStatus.CANCELLED) job
                        else job.copy(
                            status = if (isProcessing) DownloadStatus.PROCESSING else DownloadStatus.DOWNLOADING,
                            progress = if (isProcessing) 99f else progress.coerceIn(0f, 99.9f),
                            etaSeconds = eta,
                            message = if (isProcessing) {
                                "FFmpeg zpracovává a konvertuje..."
                            } else if (progress >= 0f) {
                                String.format("Stahování: %.1f%%", progress)
                            } else {
                                "Stahuji..."
                            }
                        )
                    }
                }

                updateDownloadJob(jobId) { job ->
                    if (job.status == DownloadStatus.CANCELLED) job
                    else job.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100f,
                        etaSeconds = -1L,
                        message = "Hotovo! Zpracováno přes FFmpeg.",
                        outputFilePath = outFile?.absolutePath,
                        outputFileName = outFile?.name
                    )
                }
                refreshLibrary()
            } catch (e: Exception) {
                updateDownloadJob(jobId) { job ->
                    if (job.status == DownloadStatus.CANCELLED) job
                    else job.copy(
                        status = DownloadStatus.ERROR,
                        message = "Chyba: ${e.localizedMessage?.take(140) ?: "Stahování selhalo"}"
                    )
                }
            }
        }
    }

    private fun updateDownloadJob(jobId: String, transform: (DownloadJob) -> DownloadJob) {
        _uiState.update { state ->
            state.copy(
                downloads = state.downloads.map { if (it.id == jobId) transform(it) else it }
            )
        }
    }

    fun cancelOrRemoveDownload(jobId: String) {
        val job = _uiState.value.downloads.find { it.id == jobId }
        if (job != null && (job.status == DownloadStatus.STARTING || job.status == DownloadStatus.DOWNLOADING || job.status == DownloadStatus.PROCESSING)) {
            ytDlpRepo.cancelDownload(jobId)
            updateDownloadJob(jobId) {
                it.copy(status = DownloadStatus.CANCELLED, message = "Zrušeno uživatelem.")
            }
        } else {
            _uiState.update { state ->
                state.copy(downloads = state.downloads.filterNot { it.id == jobId })
            }
        }
    }

    // --- Local Library ---
    fun refreshLibrary() {
        viewModelScope.launch {
            val files = ytDlpRepo.listLibraryFiles()
            _uiState.update { it.copy(libraryFiles = files) }
        }
    }

    fun playLocalFile(file: LocalMediaFile) {
        playMedia(
            MediaItem(
                id = file.path,
                title = file.name,
                url = "",
                isLocal = true,
                localPath = file.path,
                ext = file.ext
            )
        )
    }

    fun shareLocalFile(file: LocalMediaFile) {
        val ctx = getApplication<Application>()
        val f = File(file.path)
        if (!f.exists()) return
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.isAudio) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Sdílet soubor").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(chooser)
    }

    fun deleteLocalFile(file: LocalMediaFile) {
        viewModelScope.launch {
            ytDlpRepo.deleteLibraryFile(file.path)
            resumePositionStore.clearPosition("local:${file.path}")
            refreshLibrary()
        }
    }

    // --- Local FFmpeg Processing ---
    fun openLocalFfmpegDialog(file: LocalMediaFile) {
        if (_uiState.value.localFfmpegJob.isProcessing) return
        _uiState.update {
            it.copy(localFfmpegJob = LocalFfmpegJobState(targetFile = file))
        }
    }

    fun closeLocalFfmpegDialog() {
        if (_uiState.value.localFfmpegJob.isProcessing) return
        _uiState.update {
            it.copy(localFfmpegJob = LocalFfmpegJobState())
        }
    }

    fun startLocalFfmpegProcessing(
        file: LocalMediaFile,
        action: LocalFfmpegAction,
        trimStart: String,
        trimEnd: String
    ) {
        if (_uiState.value.localFfmpegJob.isProcessing) return
        _uiState.update {
            it.copy(
                localFfmpegJob = LocalFfmpegJobState(
                    targetFile = file,
                    isProcessing = true,
                    progress = 0f,
                    statusMessage = "Spouštím lokální FFmpeg..."
                )
            )
        }
        localFfmpegCoroutineJob = viewModelScope.launch {
            val result = localFfmpegProcessor.processFile(
                inputFile = file,
                action = action,
                trimStart = trimStart,
                trimEnd = trimEnd
            ) { progress, statusMessage ->
                _uiState.update { state ->
                    state.copy(
                        localFfmpegJob = state.localFfmpegJob.copy(
                            progress = progress,
                            statusMessage = statusMessage
                        )
                    )
                }
            }
            result.onSuccess { outFile ->
                refreshLibrary()
                _uiState.update { state ->
                    state.copy(
                        localFfmpegJob = LocalFfmpegJobState(),
                        statusBanner = "✅ FFmpeg hotovo: ${outFile.name}"
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        localFfmpegJob = state.localFfmpegJob.copy(
                            isProcessing = false,
                            progress = 0f,
                            statusMessage = "❌ Chyba: ${err.localizedMessage ?: "Zpracování selhalo"}"
                        )
                    )
                }
            }
        }
    }

    // --- Graceful App Exit ---
    fun requestExitApp() {
        _uiState.update { it.copy(showExitConfirmDialog = true) }
    }

    fun dismissExitDialog() {
        _uiState.update { it.copy(showExitConfirmDialog = false) }
    }

    fun confirmExitApp() {
        if (isShuttingDown) return
        isShuttingDown = true

        // 1) Save current playback resume position before releasing ExoPlayer
        saveCurrentResumePosition()
        positionUpdaterJob?.cancel()
        positionUpdaterJob = null

        // 2) Cancel voice listening and close open dialogs
        wasPlayingBeforeVoice = false
        cancelVoiceIfListening()
        voiceSearchController?.destroy()
        voiceSearchController = null
        _uiState.update {
            it.copy(
                showExitConfirmDialog = false,
                showEqualizerDialog = false,
                showSleepTimerDialog = false,
                showCookiesDialog = false,
                showReadmeDialog = false,
                downloadModalTarget = null,
                localFfmpegJob = LocalFfmpegJobState(),
                isVoiceListening = false,
                isPlaying = false
            )
        }

        // 3) Cancel sleep timer and search coroutines
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        searchJob?.cancel()
        searchJob = null

        // 4) Cancel active yt-dlp downloads and local FFmpeg jobs
        _uiState.value.downloads
            .filter {
                it.status == DownloadStatus.STARTING ||
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.PROCESSING
            }
            .forEach { job ->
                ytDlpRepo.cancelDownload(job.id)
                updateDownloadJob(job.id) { current ->
                    current.copy(status = DownloadStatus.CANCELLED, message = "Ukončeno při zavření aplikace.")
                }
            }
        localFfmpegCoroutineJob?.cancel()
        localFfmpegCoroutineJob = null
        localFfmpegProcessor.cancelAll()

        // 5) Stop player & foreground PlaybackService cleanly
        playerController.shutdownAll()

        // 6) Finish Activity & remove task
        _exitAppEvents.tryEmit(Unit)
    }

    private fun mediaKey(item: MediaItem): String =
        if (item.isLocal) "local:${item.localPath}" else item.url

    override fun onCleared() {
        super.onCleared()
        if (!isShuttingDown) {
            saveCurrentResumePosition()
        }
        positionUpdaterJob?.cancel()
        sleepTimerJob?.cancel()
        localFfmpegCoroutineJob?.cancel()
        searchJob?.cancel()
        localFfmpegProcessor.cancelAll()
        voiceSearchController?.destroy()
        voiceSearchController = null
        playerController.release()
    }
}
