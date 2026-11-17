package com.ffmpegplaydownloader.app.model

import com.ffmpegplaydownloader.app.player.RepeatModeOption
import com.ffmpegplaydownloader.app.player.SleepTimerPreset

enum class MediaPlatform(
    val displayName: String,
    val shortLabel: String
) {
    YOUTUBE("YouTube", "YouTube"),
    YOUTUBE_MUSIC("YouTube Music", "YT Music"),
    OTHER("Web", "Web"),
    LOCAL("Lokální", "Lokální")
}

enum class SearchSourceFilter(
    val label: String,
    val shortLabel: String
) {
    ALL("✨ Vše (YT + YT Music)", "Vše"),
    YOUTUBE("▶ YouTube", "YouTube"),
    YOUTUBE_MUSIC("🎧 YouTube Music", "YT Music")
}

enum class ResultPlatformFilter(val label: String) {
    ALL("Vše"),
    YOUTUBE("YouTube"),
    YOUTUBE_MUSIC("YT Music"),
    OTHER("Ostatní")
}

data class MediaItem(
    val id: String,
    val title: String,
    val url: String,
    val channel: String = "",
    val durationSec: Long = 0L,
    val durationString: String = "",
    val viewCount: Long? = null,
    val thumbnail: String = "",
    val extractor: String = "Web",
    val platform: MediaPlatform = MediaPlatform.YOUTUBE,
    val contentKind: String = "",
    val relevanceScore: Int = 0,
    val isLocal: Boolean = false,
    val localPath: String = "",
    val ext: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val playedAt: Long = 0L
)

data class SubtitleInfo(
    val available: Boolean = false,
    val isAuto: Boolean = false,
    val lang: String = "",
    val label: String = "Nedostupné",
    val url: String = "",
    val ext: String = "vtt"
)

data class ResolvedStreams(
    val id: String,
    val title: String,
    val channel: String,
    val durationSec: Long,
    val durationString: String,
    val thumbnail: String,
    val progressiveUrl: String,
    val videoUrl: String,
    val audioUrl: String,
    val userAgent: String,
    val referer: String,
    val czechSubtitles: SubtitleInfo,
    val platform: MediaPlatform = MediaPlatform.YOUTUBE,
    val fastVideoUrl: String = "",
    val cookieHeader: String = ""
)

enum class StreamMode(val label: String) {
    FAST_VIDEO("⚡ Rychlé video (MP4)"),
    HQ_DASH_VIDEO("🎞️ HQ Mux (1080p+ video+zvuk)"),
    AUDIO_ONLY("🎵 Pouze zvuk (Audio stream)")
}

enum class AudioFilterMode(val label: String) {
    NONE("Bez filtru (Originál)"),
    LOUDNESS("🔊 Normalizace hlasitosti"),
    BASS_BOOST("🎸 Bass Boost (+ basy)")
}

enum class EqualizerPreset(
    val label: String,
    val bandLevelsMb: List<Int>
) {
    FLAT("Plochý", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("🎸 Bass Boost", listOf(700, 450, 0, 100, 200)),
    ROCK("🤘 Rock", listOf(500, 250, -150, 350, 600)),
    POP("🎤 Pop", listOf(-150, 300, 500, 250, -150)),
    JAZZ("🎷 Jazz", listOf(400, 200, -150, 300, 450)),
    CLASSICAL("🎻 Klasika", listOf(450, 250, -100, 300, 500)),
    ELECTRONIC("🎧 Elektronika", listOf(650, 400, -150, 300, 650)),
    VOCAL("🎙️ Vokály", listOf(-300, 100, 550, 450, 150)),
    CUSTOM("🎚️ Vlastní", listOf(0, 0, 0, 0, 0))
}

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val label: String,
    val levelMb: Int = 0
)

data class EqualizerState(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val bassBoostStrength: Int = 0,
    val loudnessEnabled: Boolean = false,
    val bands: List<EqualizerBand> = defaultBands()
) {
    companion object {
        fun defaultBands(): List<EqualizerBand> = listOf(
            EqualizerBand(0, 60, "60 Hz", 0),
            EqualizerBand(1, 230, "230 Hz", 0),
            EqualizerBand(2, 910, "910 Hz", 0),
            EqualizerBand(3, 3600, "3.6 kHz", 0),
            EqualizerBand(4, 14000, "14 kHz", 0)
        )
    }
}

enum class DownloadFormat(
    val id: String,
    val label: String,
    val isAudio: Boolean
) {
    MP3_320("mp3-320", "MP3 — 320 kbps (Nejvyšší kvalita + obal & tagy)", true),
    MP3_192("mp3-192", "MP3 — 192 kbps (Kompaktní + obal)", true),
    FLAC("flac", "FLAC — Bezztrátové studiové audio", true),
    M4A("m4a", "M4A (AAC) — Vysoká kvalita s obalem", true),
    WAV("wav", "WAV — Nekonvertované PCM audio", true),
    OPUS("opus", "OPUS — Vysoce efektivní audio", true),
    MP4_1080("mp4-1080", "MP4 Video — Full HD (1080p + AAC zvuk)", false),
    MP4_BEST("mp4-best", "MP4 Video — Maximální rozlišení (4K / 2K)", false),
    MP4_720("mp4-720", "MP4 Video — HD (720p)", false),
    MP4_480("mp4-480", "MP4 Video — Úsporné (480p)", false),
    MKV_BEST("mkv-best", "MKV Video — Bezztrátové sloučení stop", false)
}

enum class DownloadStatus {
    STARTING,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    ERROR,
    CANCELLED
}

data class DownloadJob(
    val id: String,
    val url: String,
    val title: String,
    val format: DownloadFormat,
    val trimStart: String = "",
    val trimEnd: String = "",
    val embedSubs: Boolean = true,
    val loudnorm: Boolean = false,
    val status: DownloadStatus = DownloadStatus.STARTING,
    val progress: Float = 0f,
    val etaSeconds: Long = -1L,
    val message: String = "Připravuji stahování...",
    val outputFilePath: String? = null,
    val outputFileName: String? = null
)

data class LocalMediaFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val ext: String,
    val isAudio: Boolean
)

data class PlaybackExtrasState(
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val shuffleEnabled: Boolean = false,
    val sleepTimerPreset: SleepTimerPreset = SleepTimerPreset.OFF,
    val sleepTimerRemainingSec: Long = 0L
)

data class LocalFfmpegJobState(
    val targetFile: LocalMediaFile? = null,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = ""
)

enum class MainTab(val title: String) {
    SEARCH("Hledat"),
    QUEUE("Fronta"),
    FAVORITES("Oblíbené"),
    HISTORY("Historie"),
    DOWNLOADS("Stahování"),
    LIBRARY("Knihovna")
}
