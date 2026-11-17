package com.ffmpegplaydownloader.app.player

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
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
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ffmpegplaydownloader.app.model.EqualizerBand
import com.ffmpegplaydownloader.app.model.EqualizerState
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
    private val onPlaybackError: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
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
    private var didFastVideoFallbackToMerge: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onPlaybackEnded()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                applyEqualizerState(currentEqualizerState)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val streams = lastOnlineStreams ?: return
            if (lastStreamMode != StreamMode.FAST_VIDEO || didFastVideoFallbackToMerge) return

            val hasVideoTrack = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
            val hasAudioTrack = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
            if (hasVideoTrack && !hasAudioTrack && streams.audioUrl.isNotBlank()) {
                fallbackFastVideoToMerge()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val streams = lastOnlineStreams
            if (lastStreamMode == StreamMode.FAST_VIDEO &&
                !didFastVideoFallbackToMerge &&
                streams != null &&
                streams.fastVideoUrl.isNotBlank() &&
                streams.audioUrl.isNotBlank() &&
                fallbackFastVideoToMerge()
            ) {
                return
            }
            onPlaybackError("Chyba přehrávače: ${error.errorCodeName}")
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

    fun playLocalFile(
        filePath: String,
        title: String,
        startPositionMs: Long = 0L
    ) {
        ensureBackgroundServiceConnected()
        lastOnlineStreams = null
        didFastVideoFallbackToMerge = false
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
        val mime = when (streams.czechSubtitles.ext.lowercase()) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ttml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.TEXT_VTT
        }
        return ExoMediaItem.SubtitleConfiguration.Builder(Uri.parse(streams.czechSubtitles.url))
            .setMimeType(mime)
            .setLanguage("cs")
            .setLabel("Čeština")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    private fun playMergedVideoAndAudio(
        videoUri: String,
        audioUri: String,
        httpFactory: DefaultHttpDataSource.Factory,
        mediaSourceFactory: DefaultMediaSourceFactory,
        metadata: MediaMetadata,
        subtitleConfig: ExoMediaItem.SubtitleConfiguration?
    ): Boolean {
        val sources = mutableListOf<MediaSource>()
        val primaryUri = videoUri.ifBlank { audioUri }
        val videoItem = ExoMediaItem.Builder()
            .setUri(primaryUri)
            .setMediaMetadata(metadata)
            .build()

        if (videoUri.isNotBlank() && audioUri.isNotBlank() && videoUri != audioUri) {
            sources += mediaSourceFactory.createMediaSource(videoItem)
            sources += mediaSourceFactory.createMediaSource(ExoMediaItem.fromUri(audioUri))
        } else if (primaryUri.isNotBlank()) {
            sources += mediaSourceFactory.createMediaSource(videoItem)
        } else {
            return false
        }

        if (subtitleConfig != null) {
            sources += SingleSampleMediaSource.Factory(httpFactory)
                .createMediaSource(subtitleConfig, C.TIME_UNSET)
        }

        when {
            sources.size == 1 -> exoPlayer.setMediaSource(sources.first())
            sources.isNotEmpty() -> exoPlayer.setMediaSource(MergingMediaSource(*sources.toTypedArray()))
        }
        return true
    }

    private fun fallbackFastVideoToMerge(): Boolean {
        val streams = lastOnlineStreams ?: return false
        if (lastStreamMode != StreamMode.FAST_VIDEO || didFastVideoFallbackToMerge) return false

        val vUrl = streams.fastVideoUrl.ifBlank { streams.videoUrl }
        val aUrl = streams.audioUrl
        if (vUrl.isBlank() || aUrl.isBlank() || (vUrl == streams.progressiveUrl && aUrl == streams.progressiveUrl)) {
            return false
        }

        didFastVideoFallbackToMerge = true
        val resumePos = exoPlayer.currentPosition.coerceAtLeast(0L)
        val httpFactory = createHttpFactory(streams)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        val metadata = buildMetadata(streams)
        val subtitleConfig = buildSubtitleConfig(streams, lastSubtitlesEnabled)

        playMergedVideoAndAudio(
            videoUri = vUrl,
            audioUri = aUrl,
            httpFactory = httpFactory,
            mediaSourceFactory = mediaSourceFactory,
            metadata = metadata,
            subtitleConfig = subtitleConfig
        )
        setSubtitlesEnabled(lastSubtitlesEnabled)
        exoPlayer.prepare()
        if (resumePos > 0L) {
            exoPlayer.seekTo(resumePos)
        }
        exoPlayer.playWhenReady = true
        return true
    }

    fun playOnlineStreams(
        streams: ResolvedStreams,
        streamMode: StreamMode,
        subtitlesEnabled: Boolean,
        startPositionMs: Long = 0L
    ) {
        ensureBackgroundServiceConnected()
        lastOnlineStreams = streams
        lastStreamMode = streamMode
        lastSubtitlesEnabled = subtitlesEnabled
        didFastVideoFallbackToMerge = false

        val httpFactory = createHttpFactory(streams)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
        val metadata = buildMetadata(streams)
        val subtitleConfig = buildSubtitleConfig(streams, subtitlesEnabled)

        val preparedSource = when (streamMode) {
            StreamMode.AUDIO_ONLY -> {
                val url = streams.audioUrl.ifBlank { streams.progressiveUrl }
                if (url.isBlank()) {
                    false
                } else {
                    val item = ExoMediaItem.Builder()
                        .setUri(url)
                        .setMediaMetadata(metadata)
                        .build()
                    exoPlayer.setMediaSource(mediaSourceFactory.createMediaSource(item))
                    true
                }
            }
            StreamMode.HQ_DASH_VIDEO -> {
                val vUrl = streams.videoUrl.ifBlank { streams.progressiveUrl }
                val aUrl = streams.audioUrl.ifBlank { streams.progressiveUrl }
                playMergedVideoAndAudio(
                    videoUri = vUrl,
                    audioUri = aUrl,
                    httpFactory = httpFactory,
                    mediaSourceFactory = mediaSourceFactory,
                    metadata = metadata,
                    subtitleConfig = subtitleConfig
                )
            }
            StreamMode.FAST_VIDEO -> {
                val fastCandidate = streams.fastVideoUrl.ifBlank { streams.videoUrl }
                val hasSeparateAudio = streams.audioUrl.isNotBlank() && streams.audioUrl != streams.progressiveUrl
                val useProgressiveDirect = streams.progressiveUrl.isNotBlank() &&
                    !(streams.progressiveUrl == fastCandidate && hasSeparateAudio)

                if (useProgressiveDirect) {
                    val builder = ExoMediaItem.Builder()
                        .setUri(streams.progressiveUrl)
                        .setMediaMetadata(metadata)
                    if (subtitleConfig != null) {
                        builder.setSubtitleConfigurations(listOf(subtitleConfig))
                    }
                    exoPlayer.setMediaSource(mediaSourceFactory.createMediaSource(builder.build()))
                    true
                } else {
                    didFastVideoFallbackToMerge = true
                    playMergedVideoAndAudio(
                        videoUri = fastCandidate,
                        audioUri = streams.audioUrl.ifBlank { streams.progressiveUrl },
                        httpFactory = httpFactory,
                        mediaSourceFactory = mediaSourceFactory,
                        metadata = metadata,
                        subtitleConfig = subtitleConfig
                    )
                }
            }
        }

        if (!preparedSource) {
            onPlaybackError("Chyba přehrávače: Nebyla nalezena žádná přehratelná adresa streamu.")
            return
        }

        setSubtitlesEnabled(subtitlesEnabled)

        exoPlayer.prepare()
        if (startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        lastSubtitlesEnabled = enabled
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setPreferredTextLanguage(if (enabled) "cs" else null)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    fun seekToMs(positionMs: Long) {
        val duration = exoPlayer.duration
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
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
