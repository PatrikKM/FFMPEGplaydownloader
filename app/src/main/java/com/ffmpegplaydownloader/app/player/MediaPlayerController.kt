package com.ffmpegplaydownloader.app.player

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ffmpegplaydownloader.app.model.EqualizerBand
import com.ffmpegplaydownloader.app.model.EqualizerState
import com.ffmpegplaydownloader.app.model.PlaybackAttempt
import com.ffmpegplaydownloader.app.model.ResolvedStreams
import com.ffmpegplaydownloader.app.model.StreamMode
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
class MediaPlayerController(
    context: Context,
    private val onPlaybackEnded: () -> Unit,
    private val onHardwareEqualizerReady: (minMb: Int, maxMb: Int, hardwareBands: List<EqualizerBand>) -> Unit = { _, _, _ -> },
    private val onPlaybackError: (String) -> Unit = {},
    private val onAutoRecovered: (String) -> Unit = {},
    private val onRequestStreamRefresh: (() -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    val exoPlayer: ExoPlayer = PlayerEngine.getOrCreatePlayer(appContext)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var released: Boolean = false

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var activeAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var currentEqualizerState: EqualizerState = EqualizerState()

    private var lastOnlineStreams: ResolvedStreams? = null
    private var lastStreamMode: StreamMode = StreamMode.FAST_VIDEO
    private var lastSubtitlesEnabled: Boolean = true

    private var attemptQueue: List<PlaybackAttempt> = emptyList()
    private var currentAttemptIndex: Int = 0
    private var playbackGeneration: Long = 0L
    private var pendingRecoveryRunnable: Runnable? = null
    private var missingAudioReportedForAttemptIndex: Int = -1
    private var hasRequestedRefreshForCurrentTrack: Boolean = false
    private var pendingResumePositionMs: Long = 0L
    private var lastPlaybackError: PlaybackException? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                pendingResumePositionMs = 0L
            } else if (playbackState == Player.STATE_ENDED) {
                onPlaybackEnded()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                applyEqualizerState(currentEqualizerState)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (released || lastOnlineStreams == null) return
            val attempt = attemptQueue.getOrNull(currentAttemptIndex) ?: return
            if (attempt.isAudioOnly || lastStreamMode == StreamMode.AUDIO_ONLY) return
            if (tracks.groups.isEmpty()) return

            val hasVideoTrack = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
            val hasAudioTrack = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
            val hasFallbackRemaining = (currentAttemptIndex + 1 < attemptQueue.size) ||
                (!hasRequestedRefreshForCurrentTrack && onRequestStreamRefresh != null)

            if (hasVideoTrack && !hasAudioTrack && hasFallbackRemaining &&
                missingAudioReportedForAttemptIndex != currentAttemptIndex
            ) {
                missingAudioReportedForAttemptIndex = currentAttemptIndex
                scheduleAttemptRecovery(
                    reason = "chybějící zvuková stopa",
                    expectedGeneration = playbackGeneration
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (released) return
            if (lastOnlineStreams == null) {
                onPlaybackError(formatFriendlyPlaybackError(error, error.errorCodeName))
                return
            }
            lastPlaybackError = error
            scheduleAttemptRecovery(
                reason = error.errorCodeName,
                expectedGeneration = playbackGeneration
            )
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        ensureBackgroundServiceConnected()
    }

    private fun ensureBackgroundServiceConnected() {
        if (controllerFuture != null) return
        try {
            val sessionToken = SessionToken(
                appContext,
                ComponentName(appContext, PlaybackService::class.java)
            )
            val future = MediaController.Builder(appContext, sessionToken).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    mediaController = future.get()
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(appContext))
        } catch (_: Exception) {
        }
    }

    private fun cancelPendingRecovery() {
        pendingRecoveryRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRecoveryRunnable = null
    }

    private fun isHlsOrDashUri(uri: String): Boolean {
        val lower = uri.lowercase(Locale.ROOT)
        return lower.contains(".m3u8") ||
            lower.contains("/manifest/hls") ||
            lower.contains(".mpd") ||
            lower.contains("/manifest/dash")
    }

    private fun formatFriendlyPlaybackError(
        error: PlaybackException?,
        fallbackReason: String
    ): String {
        return when (error?.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                "Nepodařilo se připojit ke streamu. Zkontrolujte internetové připojení."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "Stream vypršel nebo je dočasně nedostupný. Zkuste přehrát položku znovu."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                "Zařízení nepodporuje formát tohoto videa. Zkuste jiný režim nebo Pouze zvuk."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                "Nepodařilo se zpracovat formát streamu. Zkuste jiný režim přehrávání."
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK ->
                "Přehrávač narazil na nekompatibilní stopu. Zkuste přepnout režim přehrávání nebo Pouze zvuk."
            else -> if (fallbackReason.contains("chybějící zvuková stopa", ignoreCase = true)) {
                "Stream neobsahuje kompatibilní zvukovou stopu."
            } else {
                "Přehrávání tohoto streamu se nezdařilo. Zkuste jiný režim přehrávání."
            }
        }
    }

    fun playLocalFile(
        filePath: String,
        title: String,
        startPositionMs: Long = 0L
    ) {
        if (released) return
        ensureBackgroundServiceConnected()
        playbackGeneration++
        cancelPendingRecovery()
        lastOnlineStreams = null
        attemptQueue = emptyList()
        currentAttemptIndex = 0
        missingAudioReportedForAttemptIndex = -1
        hasRequestedRefreshForCurrentTrack = false
        pendingResumePositionMs = 0L
        lastPlaybackError = null

        val uri = Uri.fromFile(File(filePath))
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist("Lokální knihovna")
            .build()
        val mediaItem = ExoMediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    private fun createHttpFactory(streams: ResolvedStreams): DefaultHttpDataSource.Factory {
        val headers = buildMap {
            if (streams.referer.isNotBlank()) put("Referer", streams.referer)
            if (streams.cookieHeader.isNotBlank()) put("Cookie", streams.cookieHeader)
        }
        return DefaultHttpDataSource.Factory()
            .setUserAgent(streams.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
    }

    private fun buildMetadata(streams: ResolvedStreams): MediaMetadata =
        MediaMetadata.Builder()
            .setTitle(streams.title)
            .setArtist(streams.channel)
            .apply {
                if (streams.thumbnail.isNotBlank()) {
                    setArtworkUri(Uri.parse(streams.thumbnail))
                }
            }
            .build()

    private fun buildSubtitleConfig(
        streams: ResolvedStreams,
        subtitlesEnabled: Boolean
    ): ExoMediaItem.SubtitleConfiguration? {
        if (!subtitlesEnabled || !streams.czechSubtitles.available || streams.czechSubtitles.url.isBlank()) {
            return null
        }
        val mime = when (streams.czechSubtitles.ext.lowercase(Locale.ROOT)) {
            "vtt" -> MimeTypes.TEXT_VTT
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ttml" -> MimeTypes.APPLICATION_TTML
            else -> return null
        }
        return ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(streams.czechSubtitles.url))
            .setMimeType(mime)
            .setLanguage("cs")
            .setLabel("Čeština")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    private fun buildLegacyAttempts(
        streams: ResolvedStreams,
        streamMode: StreamMode
    ): List<PlaybackAttempt> {
        return when (streamMode) {
            StreamMode.AUDIO_ONLY -> {
                val url = streams.audioUrl.ifBlank { streams.progressiveUrl }
                if (url.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        PlaybackAttempt(
                            label = "Audio stream",
                            videoUrl = url,
                            audioUrl = "",
                            isHlsOrDash = isHlsOrDashUri(url),
                            includeSubtitles = false,
                            isAudioOnly = true
                        )
                    )
                }
            }
            StreamMode.HQ_DASH_VIDEO -> {
                val vUrl = streams.videoUrl.ifBlank { streams.progressiveUrl }
                val aUrl = streams.audioUrl.ifBlank { streams.progressiveUrl }
                buildList {
                    if (vUrl.isNotBlank()) {
                        val vIsHls = isHlsOrDashUri(vUrl)
                        val aIsHls = aUrl.isNotBlank() && isHlsOrDashUri(aUrl)
                        if (vIsHls || aIsHls || aUrl.isBlank() || vUrl == aUrl) {
                            add(
                                PlaybackAttempt(
                                    label = if (vIsHls) "HLS stream" else "HQ video",
                                    videoUrl = vUrl,
                                    audioUrl = "",
                                    isHlsOrDash = vIsHls
                                )
                            )
                        } else {
                            add(
                                PlaybackAttempt(
                                    label = "HQ video + audio",
                                    videoUrl = vUrl,
                                    audioUrl = aUrl,
                                    isHlsOrDash = false
                                )
                            )
                        }
                    }
                    if (streams.progressiveUrl.isNotBlank() && streams.progressiveUrl != vUrl) {
                        add(
                            PlaybackAttempt(
                                label = "Záložní stream",
                                videoUrl = streams.progressiveUrl,
                                audioUrl = "",
                                isHlsOrDash = isHlsOrDashUri(streams.progressiveUrl)
                            )
                        )
                    }
                }
            }
            StreamMode.FAST_VIDEO -> {
                val fastCandidate = streams.fastVideoUrl.ifBlank { streams.videoUrl }
                val hasSeparateAudio = streams.audioUrl.isNotBlank() && streams.audioUrl != streams.progressiveUrl
                buildList {
                    if (streams.progressiveUrl.isNotBlank() &&
                        !(streams.progressiveUrl == fastCandidate && hasSeparateAudio)
                    ) {
                        add(
                            PlaybackAttempt(
                                label = "Rychlý stream",
                                videoUrl = streams.progressiveUrl,
                                audioUrl = "",
                                isHlsOrDash = isHlsOrDashUri(streams.progressiveUrl)
                            )
                        )
                    }
                    if (fastCandidate.isNotBlank() && !isHlsOrDashUri(fastCandidate) &&
                        streams.audioUrl.isNotBlank() && !isHlsOrDashUri(streams.audioUrl)
                    ) {
                        add(
                            PlaybackAttempt(
                                label = "Kompatibilní video + audio",
                                videoUrl = fastCandidate,
                                audioUrl = streams.audioUrl,
                                isHlsOrDash = false
                            )
                        )
                    } else if (fastCandidate.isNotBlank()) {
                        add(
                            PlaybackAttempt(
                                label = "Záložní stream",
                                videoUrl = fastCandidate,
                                audioUrl = "",
                                isHlsOrDash = isHlsOrDashUri(fastCandidate)
                            )
                        )
                    }
                    if (streams.audioUrl.isNotBlank()) {
                        add(
                            PlaybackAttempt(
                                label = "Pouze zvuk (záložní)",
                                videoUrl = streams.audioUrl,
                                audioUrl = "",
                                isHlsOrDash = isHlsOrDashUri(streams.audioUrl),
                                includeSubtitles = false,
                                isAudioOnly = true
                            )
                        )
                    }
                }
            }
        }
    }

    private fun buildAttemptQueue(
        streams: ResolvedStreams,
        streamMode: StreamMode,
        subtitlesEnabled: Boolean
    ): List<PlaybackAttempt> {
        val baseAttempts = when (streamMode) {
            StreamMode.FAST_VIDEO -> streams.fastVideoAttempts
            StreamMode.HQ_DASH_VIDEO -> streams.hqVideoAttempts
            StreamMode.AUDIO_ONLY -> streams.audioAttempts
        }.ifEmpty { buildLegacyAttempts(streams, streamMode) }

        val validSubtitleConfig = buildSubtitleConfig(streams, subtitlesEnabled)
        val expanded = mutableListOf<PlaybackAttempt>()
        val seenKeys = HashSet<String>()

        fun addUnique(attempt: PlaybackAttempt) {
            val key = "${attempt.videoUrl}|${attempt.audioUrl}|${attempt.isHlsOrDash}|${attempt.includeSubtitles}|${attempt.isAudioOnly}"
            if (seenKeys.add(key)) {
                expanded += attempt
            }
        }

        for (raw in baseAttempts) {
            val primary = raw.videoUrl.ifBlank { raw.audioUrl }
            if (primary.isBlank()) continue

            val normalized = if (raw.isHlsOrDash || isHlsOrDashUri(primary) || isHlsOrDashUri(raw.audioUrl)) {
                val standaloneUri = if (isHlsOrDashUri(primary)) primary else raw.audioUrl.ifBlank { primary }
                raw.copy(
                    videoUrl = standaloneUri,
                    audioUrl = "",
                    isHlsOrDash = true
                )
            } else {
                raw.copy(
                    videoUrl = primary,
                    audioUrl = if (raw.videoUrl.isNotBlank() && raw.audioUrl != primary) raw.audioUrl else ""
                )
            }

            if (validSubtitleConfig != null && normalized.includeSubtitles && !normalized.isAudioOnly) {
                addUnique(normalized.copy(includeSubtitles = true))
                addUnique(
                    normalized.copy(
                        label = "${normalized.label} (bez titulků)",
                        includeSubtitles = false
                    )
                )
            } else {
                addUnique(normalized.copy(includeSubtitles = false))
            }
        }
        return expanded
    }

    private fun buildMediaSourceForAttempt(
        attempt: PlaybackAttempt,
        httpFactory: DefaultHttpDataSource.Factory,
        mediaSourceFactory: DefaultMediaSourceFactory,
        progressiveFactory: ProgressiveMediaSource.Factory,
        metadata: MediaMetadata,
        subtitleConfig: ExoMediaItem.SubtitleConfiguration?
    ): MediaSource? {
        val primaryUri = attempt.videoUrl.ifBlank { attempt.audioUrl }
        if (primaryUri.isBlank()) return null

        val secondaryAudioUri = if (
            !attempt.isAudioOnly &&
            attempt.videoUrl.isNotBlank() &&
            attempt.audioUrl.isNotBlank() &&
            attempt.audioUrl != attempt.videoUrl
        ) {
            attempt.audioUrl
        } else {
            ""
        }

        val primaryIsHlsOrDash = attempt.isHlsOrDash || isHlsOrDashUri(primaryUri)
        if (primaryIsHlsOrDash) {
            val builder = ExoMediaItem.Builder()
                .setUri(primaryUri)
                .setMediaMetadata(metadata)
            if (subtitleConfig != null && attempt.includeSubtitles && !attempt.isAudioOnly) {
                builder.setSubtitleConfigurations(listOf(subtitleConfig))
            }
            return mediaSourceFactory.createMediaSource(builder.build())
        }

        val primaryItem = ExoMediaItem.Builder()
            .setUri(primaryUri)
            .setMediaMetadata(metadata)
            .build()
        val primarySource = progressiveFactory.createMediaSource(primaryItem)

        val sources = mutableListOf<MediaSource>(primarySource)
        if (secondaryAudioUri.isNotBlank() && !isHlsOrDashUri(secondaryAudioUri)) {
            sources += progressiveFactory.createMediaSource(ExoMediaItem.fromUri(secondaryAudioUri))
        }
        if (subtitleConfig != null && attempt.includeSubtitles && !attempt.isAudioOnly) {
            sources += SingleSampleMediaSource.Factory(httpFactory)
                .createMediaSource(subtitleConfig, C.TIME_UNSET)
        }

        return if (sources.size == 1) {
            sources.first()
        } else {
            MergingMediaSource(*sources.toTypedArray())
        }
    }

    private fun startAttempt(
        index: Int,
        seekPositionMs: Long,
        isAutoRecovery: Boolean
    ) {
        if (released) return
        val streams = lastOnlineStreams ?: return
        val attempt = attemptQueue.getOrNull(index) ?: return

        currentAttemptIndex = index
        missingAudioReportedForAttemptIndex = -1

        val httpFactory = createHttpFactory(streams)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        val progressiveFactory = ProgressiveMediaSource.Factory(httpFactory)
        val metadata = buildMetadata(streams)
        val subtitleConfig = if (attempt.includeSubtitles && !attempt.isAudioOnly) {
            buildSubtitleConfig(streams, lastSubtitlesEnabled)
        } else {
            null
        }

        val mediaSource = try {
            buildMediaSourceForAttempt(
                attempt = attempt,
                httpFactory = httpFactory,
                mediaSourceFactory = mediaSourceFactory,
                progressiveFactory = progressiveFactory,
                metadata = metadata,
                subtitleConfig = subtitleConfig
            )
        } catch (_: Exception) {
            null
        }

        if (mediaSource == null) {
            scheduleAttemptRecovery(
                reason = "neplatný zdroj streamu",
                expectedGeneration = playbackGeneration
            )
            return
        }

        try {
            applySubtitleTrackSelection(lastSubtitlesEnabled && attempt.includeSubtitles && !attempt.isAudioOnly)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            if (seekPositionMs > 0L) {
                exoPlayer.seekTo(seekPositionMs)
            }
            exoPlayer.playWhenReady = true
            if (isAutoRecovery) {
                onAutoRecovered("⚡ Automaticky zvolen kompatibilní formát: ${attempt.label}")
            }
        } catch (_: Exception) {
            scheduleAttemptRecovery(
                reason = "chyba inicializace přehrávače",
                expectedGeneration = playbackGeneration
            )
        }
    }

    private fun scheduleAttemptRecovery(
        reason: String,
        expectedGeneration: Long
    ) {
        if (released || expectedGeneration != playbackGeneration) return
        val currentPos = exoPlayer.currentPosition.takeIf { it > 0L } ?: pendingResumePositionMs
        if (currentPos > 0L) {
            pendingResumePositionMs = currentPos
        }

        cancelPendingRecovery()
        val runnable = Runnable {
            pendingRecoveryRunnable = null
            if (released || expectedGeneration != playbackGeneration) return@Runnable
            val nextIndex = currentAttemptIndex + 1
            if (nextIndex < attemptQueue.size) {
                startAttempt(
                    index = nextIndex,
                    seekPositionMs = pendingResumePositionMs,
                    isAutoRecovery = true
                )
            } else if (!hasRequestedRefreshForCurrentTrack && onRequestStreamRefresh != null) {
                hasRequestedRefreshForCurrentTrack = true
                onAutoRecovered("🔄 Obnovuji adresu streamu pro kompatibilní formát...")
                onRequestStreamRefresh.invoke()
            } else {
                onPlaybackError(formatFriendlyPlaybackError(lastPlaybackError, reason))
            }
        }
        pendingRecoveryRunnable = runnable
        mainHandler.post(runnable)
    }

    fun playOnlineStreams(
        streams: ResolvedStreams,
        streamMode: StreamMode,
        subtitlesEnabled: Boolean,
        startPositionMs: Long = 0L,
        isRefreshRetry: Boolean = false
    ) {
        if (released) return
        ensureBackgroundServiceConnected()
        playbackGeneration++
        cancelPendingRecovery()

        lastOnlineStreams = streams
        lastStreamMode = streamMode
        lastSubtitlesEnabled = subtitlesEnabled
        missingAudioReportedForAttemptIndex = -1
        lastPlaybackError = null
        if (!isRefreshRetry) {
            hasRequestedRefreshForCurrentTrack = false
        }
        pendingResumePositionMs = startPositionMs.coerceAtLeast(0L)

        attemptQueue = buildAttemptQueue(streams, streamMode, subtitlesEnabled)
        currentAttemptIndex = 0

        if (attemptQueue.isEmpty()) {
            if (!hasRequestedRefreshForCurrentTrack && onRequestStreamRefresh != null) {
                hasRequestedRefreshForCurrentTrack = true
                val expectedGen = playbackGeneration
                val runnable = Runnable {
                    pendingRecoveryRunnable = null
                    if (!released && expectedGen == playbackGeneration) {
                        onRequestStreamRefresh.invoke()
                    }
                }
                pendingRecoveryRunnable = runnable
                mainHandler.post(runnable)
            } else {
                onPlaybackError("Nebyla nalezena žádná přehratelná adresa streamu.")
            }
            return
        }

        startAttempt(
            index = 0,
            seekPositionMs = pendingResumePositionMs,
            isAutoRecovery = false
        )
    }

    private fun applySubtitleTrackSelection(enabled: Boolean) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setPreferredTextLanguage(if (enabled) "cs" else null)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        lastSubtitlesEnabled = enabled
        applySubtitleTrackSelection(enabled)
    }

    fun seekToMs(positionMs: Long) {
        val duration = exoPlayer.duration
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        if (pendingResumePositionMs > 0L) {
            pendingResumePositionMs = target
        }
        exoPlayer.seekTo(target)
    }

    fun seekByMs(deltaMs: Long) {
        seekToMs(exoPlayer.currentPosition + deltaMs)
    }

    /**
     * Sets ExoPlayer playback speed, clamped safely to [0.25f, 3.0f].
     */
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed.coerceIn(0.25f, 3.0f))
    }

    /**
     * Returns the current ExoPlayer playback speed multiplier.
     */
    fun getPlaybackSpeed(): Float = exoPlayer.playbackParameters.speed

    /**
     * Configures ExoPlayer single-track repeat mode.
     *
     * Architecture note: Single-track repeat ([RepeatModeOption.ONE]) is handled natively
     * by ExoPlayer via [Player.REPEAT_MODE_ONE], which loops the current item seamlessly
     * without emitting [Player.STATE_ENDED]. Queue-level [RepeatModeOption.ALL] and shuffle
     * are handled in ViewModel's `onPlaybackEnded` while ExoPlayer remains in [Player.REPEAT_MODE_OFF].
     */
    fun setRepeatModeOption(repeatOne: Boolean) {
        exoPlayer.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /**
     * Convenience overload mapping [RepeatModeOption] to ExoPlayer repeat mode
     * ([Player.REPEAT_MODE_ONE] for [RepeatModeOption.ONE], [Player.REPEAT_MODE_OFF] otherwise).
     */
    fun setRepeatModeOption(option: RepeatModeOption) {
        setRepeatModeOption(option == RepeatModeOption.ONE)
    }

    /**
     * Exposes ExoPlayer's current repeat mode ([Player.REPEAT_MODE_OFF] or [Player.REPEAT_MODE_ONE]).
     */
    fun getExoRepeatMode(): Int = exoPlayer.repeatMode

    fun applyEqualizerState(state: EqualizerState) {
        currentEqualizerState = state
        val sessionId = exoPlayer.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return

        try {
            if (activeAudioSessionId != sessionId || equalizer == null) {
                releaseAudioEffects()
                activeAudioSessionId = sessionId

                val eq = Equalizer(0, sessionId)
                equalizer = eq

                val range = eq.bandLevelRange
                val minMb = range.getOrElse(0) { (-1500).toShort() }.toInt()
                val maxMb = range.getOrElse(1) { 1500.toShort() }.toInt()
                val bandCount = eq.numberOfBands.toInt().coerceAtLeast(1)

                val hwBands = (0 until bandCount).map { idx ->
                    val centerHz = (eq.getCenterFreq(idx.toShort()) / 1000).coerceAtLeast(1)
                    val existingLevel = state.bands.getOrNull(idx)?.levelMb ?: 0
                    EqualizerBand(
                        index = idx,
                        centerFreqHz = centerHz,
                        label = formatFreqLabel(centerHz),
                        levelMb = existingLevel.coerceIn(minMb, maxMb)
                    )
                }
                onHardwareEqualizerReady(minMb, maxMb, hwBands)
            }

            equalizer?.let { eq ->
                val range = eq.bandLevelRange
                val minMb = range.getOrElse(0) { (-1500).toShort() }.toInt()
                val maxMb = range.getOrElse(1) { 1500.toShort() }.toInt()
                val bandCount = eq.numberOfBands.toInt()
                for (idx in 0 until bandCount) {
                    val level = state.bands.getOrNull(idx)?.levelMb ?: 0
                    eq.setBandLevel(idx.toShort(), level.coerceIn(minMb, maxMb).toShort())
                }
                eq.enabled = state.enabled
            }

            val shouldBass = state.enabled && state.bassBoostStrength > 0
            if (shouldBass) {
                if (bassBoost == null) {
                    bassBoost = BassBoost(0, sessionId)
                }
                bassBoost?.apply {
                    if (strengthSupported) {
                        setStrength(state.bassBoostStrength.coerceIn(0, 1000).toShort())
                    }
                    enabled = true
                }
            } else {
                bassBoost?.enabled = false
            }

            val shouldLoudness = state.loudnessEnabled
            if (shouldLoudness) {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = LoudnessEnhancer(sessionId)
                }
                loudnessEnhancer?.apply {
                    setTargetGain(650)
                    enabled = true
                }
            } else {
                loudnessEnhancer?.enabled = false
            }
        } catch (_: Exception) {
        }
    }

    private fun formatFreqLabel(freqHz: Int): String = if (freqHz >= 1000) {
        val khz = freqHz / 1000.0
        val txt = if (khz % 1.0 == 0.0) {
            khz.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", khz)
        }
        "$txt kHz"
    } else {
        "$freqHz Hz"
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            loudnessEnhancer?.release()
            bassBoost?.release()
        } catch (_: Exception) {
        } finally {
            equalizer = null
            loudnessEnhancer = null
            bassBoost = null
            activeAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        }
    }

    fun release() {
        if (released) return
        released = true
        playbackGeneration++
        cancelPendingRecovery()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            exoPlayer.removeListener(playerListener)
        } catch (_: Exception) {
        }
        releaseAudioEffects()
        try {
            controllerFuture?.let {
                MediaController.releaseFuture(it)
            }
        } catch (_: Exception) {
        }
        controllerFuture = null
        mediaController = null
    }

    fun shutdownAll() {
        release()
        PlayerEngine.shutdown(appContext)
    }
}
