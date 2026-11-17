package com.ffmpegplaydownloader.app.data

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import com.ffmpegplaydownloader.app.model.DownloadFormat
import com.ffmpegplaydownloader.app.model.LocalMediaFile
import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.model.PlaybackAttempt
import com.ffmpegplaydownloader.app.model.ResolvedStreams
import com.ffmpegplaydownloader.app.model.SearchSourceFilter
import com.ffmpegplaydownloader.app.model.SubtitleInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class YtDlpRepository(
    private val context: Context,
    private val cookiesRepository: CookiesRepository = CookiesRepository(context)
) {

    private data class CachedStream(
        val streams: ResolvedStreams,
        val expiresAt: Long
    )

    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val ytMusicClient = YouTubeMusicClient()

    fun clearStreamCache() {
        streamCache.clear()
    }

    fun invalidateStreamCache(mediaUrl: String) {
        if (mediaUrl.isBlank()) return
        for (key in streamCache.keys.toList()) {
            if (key == mediaUrl || key.startsWith("$mediaUrl::")) {
                streamCache.remove(key)
            }
        }
    }

    fun getDownloadsDirectory(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(publicDownloads, "FFMPEGplaydownloader")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
        if (appDir.exists() && appDir.canWrite()) {
            return appDir
        }
        val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "FFMPEGplaydownloader")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    suspend fun getYtDlpVersion(): String = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().version(context) ?: "Neznámá"
        } catch (_: Exception) {
            "Neznámá"
        }
    }

    suspend fun updateYtDlp(): Pair<String, String> = withContext(Dispatchers.IO) {
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
        val ver = getYtDlpVersion()
        val msg = when (status) {
            YoutubeDL.UpdateStatus.DONE -> "yt-dlp aktualizován na verzi $ver"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp je aktuální ($ver)"
            else -> "Stav aktualizace yt-dlp: $ver"
        }
        ver to msg
    }

    suspend fun searchWithPlan(
        plan: SearchPlan,
        limit: Int = 20,
        offset: Int = 0
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        if (plan.isDirectUrl) {
            return@withContext search(plan.originalQuery, limit = limit, offset = offset)
        }

        when (plan.effectiveSource) {
            SearchSourceFilter.YOUTUBE -> {
                search(plan.youtubeQuery, limit = limit, offset = offset)
            }
            SearchSourceFilter.YOUTUBE_MUSIC -> {
                if (offset == 0) {
                    val ytmResults = runCatching {
                        ytMusicClient.search(plan.youtubeMusicQuery, limit = limit)
                    }.getOrNull().orEmpty()
                    if (ytmResults.isNotEmpty()) {
                        return@withContext ytmResults
                    }
                }
                // Fallback / pagination beyond initial YouTube Music set
                search(
                    query = "${plan.youtubeMusicQuery} audio",
                    limit = limit,
                    offset = offset,
                    forcePlatform = MediaPlatform.YOUTUBE_MUSIC,
                    defaultContentKind = "Skladba"
                )
            }
            SearchSourceFilter.ALL -> {
                if (offset > 0) {
                    return@withContext search(plan.youtubeQuery, limit = limit, offset = offset)
                }
                coroutineScope {
                    val perPlatformLimit = (limit / 2 + 4).coerceAtLeast(12)
                    val ytDeferred = async {
                        runCatching { search(plan.youtubeQuery, limit = perPlatformLimit, offset = 0) }
                            .getOrElse { emptyList() }
                    }
                    val ytmDeferred = async {
                        runCatching { ytMusicClient.search(plan.youtubeMusicQuery, limit = perPlatformLimit) }
                            .getOrElse { emptyList() }
                    }
                    val ytItems = ytDeferred.await()
                    val ytmItems = ytmDeferred.await()
                    if (ytItems.isEmpty() && ytmItems.isEmpty()) {
                        // Surface a clear search call if both failed or returned empty
                        return@coroutineScope search(plan.youtubeQuery, limit = limit, offset = 0)
                    }
                    NaturalLanguageSearchEngine.scoreAndMergeResults(ytItems, ytmItems, plan)
                        .take(limit.coerceAtLeast(24))
                }
            }
        }
    }

    suspend fun search(
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        forcePlatform: MediaPlatform? = null,
        defaultContentKind: String = "Video"
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()

            val isUrl = trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            val isInputYtmUrl = isUrl && trimmed.contains("music.youtube.com", ignoreCase = true)
            val safeLimit = limit.coerceIn(1, 100)
            val safeOffset = offset.coerceAtLeast(0)
            val totalToFetch = (safeOffset + safeLimit).coerceAtMost(100)

            val target = if (isUrl) trimmed else "ytsearch$totalToFetch:$trimmed"
            val activeCookiesPath = if (isUrl) cookiesRepository.getActiveCookiesPath() else null

            val request = YoutubeDLRequest(target).apply {
                addOption("--dump-json")
                addOption("--flat-playlist")
                addOption("--no-warnings")
                addOption("--ignore-errors")
                if (safeOffset > 0) {
                    addOption("--playlist-start", (safeOffset + 1).toString())
                }
                if (!activeCookiesPath.isNullOrBlank()) {
                    addOption("--cookies", activeCookiesPath)
                }
            }

            val response = YoutubeDL.getInstance().execute(request, null, null)
            val lines = response.out.lines().filter { it.trim().startsWith("{") }

            buildList {
                for (line in lines) {
                    try {
                        val data = JSONObject(line)
                        val id = data.optString("id").ifBlank { line.hashCode().toString() }
                        val ieKey = data.optString("ie_key", data.optString("extractor_key", "Web"))
                        var webpageUrl = data.optString("webpage_url")
                            .ifBlank { data.optString("url") }
                        if (webpageUrl.isBlank() || (!webpageUrl.startsWith("http") && (ieKey.equals("Youtube", true) || !isUrl))) {
                            webpageUrl = if (isInputYtmUrl || forcePlatform == MediaPlatform.YOUTUBE_MUSIC) {
                                "https://music.youtube.com/watch?v=$id"
                            } else {
                                "https://www.youtube.com/watch?v=$id"
                            }
                        } else if (isInputYtmUrl && webpageUrl.contains("youtube.com/watch")) {
                            webpageUrl = webpageUrl.replace("www.youtube.com", "music.youtube.com")
                        }

                        val resolvedPlatform = forcePlatform ?: detectPlatform(webpageUrl, ieKey, isInputYtmUrl)
                        val cleanExtractor = when (resolvedPlatform) {
                            MediaPlatform.YOUTUBE_MUSIC -> "YouTubeMusic"
                            MediaPlatform.YOUTUBE -> "YouTube"
                            else -> ieKey.ifBlank { "Web" }
                        }

                        var thumb = data.optString("thumbnail")
                        if (thumb.isBlank()) {
                            val thumbs = data.optJSONArray("thumbnails")
                            if (thumbs != null && thumbs.length() > 0) {
                                thumb = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
                            }
                        }
                        if (thumb.isBlank() && (webpageUrl.contains("youtube.com") || webpageUrl.contains("youtu.be"))) {
                            thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                        }

                        val durSec = data.optDouble("duration", -1.0).let {
                            if (it.isNaN() || it < 0) 0L else it.toLong()
                        }
                        val durStr = data.optString("duration_string").ifBlank { formatDuration(durSec) }
                        val channel = data.optString("channel")
                            .ifBlank { data.optString("uploader") }
                            .ifBlank { "Neznámý kanál" }
                        val viewCount = if (data.has("view_count") && !data.isNull("view_count")) {
                            data.optLong("view_count")
                        } else null

                        add(
                            MediaItem(
                                id = id,
                                title = data.optString("title", "Bez názvu"),
                                url = webpageUrl,
                                channel = channel,
                                durationSec = durSec,
                                durationString = durStr,
                                viewCount = viewCount,
                                thumbnail = thumb,
                                extractor = cleanExtractor,
                                platform = resolvedPlatform,
                                contentKind = if (resolvedPlatform == MediaPlatform.YOUTUBE_MUSIC) "Skladba" else defaultContentKind
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

    private fun detectPlatform(webpageUrl: String, ieKey: String, isInputYtmUrl: Boolean): MediaPlatform {
        return when {
            isInputYtmUrl || webpageUrl.contains("music.youtube.com", ignoreCase = true) ||
                ieKey.contains("Music", ignoreCase = true) -> MediaPlatform.YOUTUBE_MUSIC
            webpageUrl.contains("youtube.com", ignoreCase = true) ||
                webpageUrl.contains("youtu.be", ignoreCase = true) ||
                ieKey.equals("Youtube", ignoreCase = true) -> MediaPlatform.YOUTUBE
            else -> MediaPlatform.OTHER
        }
    }

    private fun hasValidCodec(codec: String): Boolean =
        codec.isNotBlank() &&
            !codec.equals("none", ignoreCase = true) &&
            !codec.equals("null", ignoreCase = true)

    private data class DeviceDecoderCapabilities(
        val supportedMimeTypes: Set<String>,
        val hardwareVideoMimeTypes: Set<String>
    )

    private val deviceDecoderCapabilities: DeviceDecoderCapabilities by lazy {
        detectDeviceDecoderCapabilities()
    }

    private fun detectDeviceDecoderCapabilities(): DeviceDecoderCapabilities {
        val supported = mutableSetOf<String>()
        val hardwareVideo = mutableSetOf<String>()
        val targetMimes = setOf(
            "video/avc",
            "video/hevc",
            "video/x-vnd.on2.vp9",
            "video/x-vnd.on2.vp8",
            "video/av01",
            "audio/mp4a-latm",
            "audio/opus",
            "audio/vorbis",
            "audio/mpeg",
            "audio/flac",
            "audio/ac3",
            "audio/eac3"
        )
        return try {
            val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            for (info in codecInfos) {
                if (info.isEncoder) continue
                val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    val lowerName = info.name.lowercase()
                    !lowerName.startsWith("omx.google.") &&
                        !lowerName.startsWith("c2.android.") &&
                        !lowerName.contains(".sw.")
                }
                for (type in info.supportedTypes) {
                    val mime = type.lowercase()
                    if (mime in targetMimes || mime.startsWith("video/") || mime.startsWith("audio/")) {
                        supported += mime
                        if (isHw && mime.startsWith("video/")) {
                            hardwareVideo += mime
                        }
                    }
                }
            }
            if (supported.isEmpty()) {
                DeviceDecoderCapabilities(
                    supportedMimeTypes = setOf("video/avc", "audio/mp4a-latm", "audio/opus"),
                    hardwareVideoMimeTypes = setOf("video/avc")
                )
            } else {
                DeviceDecoderCapabilities(supported, hardwareVideo)
            }
        } catch (_: Throwable) {
            DeviceDecoderCapabilities(
                supportedMimeTypes = setOf("video/avc", "audio/mp4a-latm", "audio/opus"),
                hardwareVideoMimeTypes = setOf("video/avc")
            )
        }
    }

    private fun mapYtDlpCodecToMime(ytDlpCodec: String): String? {
        val c = ytDlpCodec.trim().lowercase()
        if (!hasValidCodec(c)) return null
        return when {
            c.startsWith("avc1") || c.startsWith("avc3") || c.contains("h264") -> "video/avc"
            c.startsWith("hev1") || c.startsWith("hvc1") || c.contains("hevc") || c.contains("h265") -> "video/hevc"
            c.startsWith("vp09") || c.startsWith("vp9") -> "video/x-vnd.on2.vp9"
            c.startsWith("vp08") || c.startsWith("vp8") -> "video/x-vnd.on2.vp8"
            c.startsWith("av01") || c == "av1" -> "video/av01"
            c.startsWith("mp4a.69") || c.startsWith("mp4a.6b") || c == "mp3" -> "audio/mpeg"
            c.startsWith("mp4a") || c.contains("aac") -> "audio/mp4a-latm"
            c.startsWith("opus") -> "audio/opus"
            c.startsWith("vorbis") -> "audio/vorbis"
            c.startsWith("flac") -> "audio/flac"
            c.startsWith("ac-3") || c == "ac3" -> "audio/ac3"
            c.startsWith("ec-3") || c == "eac3" -> "audio/eac3"
            c.contains("/") -> c
            else -> null
        }
    }

    fun isCodecSupportedByDevice(ytDlpCodec: String): Boolean {
        if (!hasValidCodec(ytDlpCodec)) return false
        val mime = mapYtDlpCodecToMime(ytDlpCodec) ?: return true
        return mime in deviceDecoderCapabilities.supportedMimeTypes
    }

    private fun hasHardwareVideoDecoder(ytDlpCodec: String): Boolean {
        val mime = mapYtDlpCodecToMime(ytDlpCodec) ?: return false
        return mime in deviceDecoderCapabilities.hardwareVideoMimeTypes
    }

    private fun isH264Codec(codec: String): Boolean {
        val c = codec.trim().lowercase()
        return c.startsWith("avc1") || c.startsWith("avc3") || c.contains("h264")
    }

    private fun isVp9Codec(codec: String): Boolean {
        val c = codec.trim().lowercase()
        return c.startsWith("vp09") || c.startsWith("vp9")
    }

    private fun isHevcCodec(codec: String): Boolean {
        val c = codec.trim().lowercase()
        return c.startsWith("hev1") || c.startsWith("hvc1") || c.contains("hevc") || c.contains("h265")
    }

    private fun isAv1Codec(codec: String): Boolean {
        val c = codec.trim().lowercase()
        return c.startsWith("av01") || c == "av1"
    }

    private fun is10BitCodec(codec: String): Boolean {
        val c = codec.trim().lowercase()
        return c.startsWith("vp09.02") ||
            c.startsWith("vp9.2") ||
            c.startsWith("hev1.2") ||
            c.startsWith("hvc1.2") ||
            c.contains(".10.")
    }

    private fun isAacOrMp4aCodec(codec: String): Boolean {
        val c = codec.trim().lowercase()
        return (c.startsWith("mp4a") && !c.startsWith("mp4a.69") && !c.startsWith("mp4a.6b")) || c.contains("aac")
    }

    private fun isOpusCodec(codec: String): Boolean =
        codec.trim().lowercase().contains("opus")

    private fun isHlsOrDashFormat(url: String, protocol: String, ext: String = ""): Boolean {
        val p = protocol.lowercase()
        val u = url.lowercase()
        val e = ext.lowercase()
        return p.contains("m3u8") ||
            p.contains("mpd") ||
            p == "dash" ||
            e == "m3u8" ||
            e == "mpd" ||
            u.contains(".m3u8") ||
            u.contains("/manifest/hls") ||
            u.contains(".mpd") ||
            u.contains("/manifest/dash")
    }

    private fun isSegmentedNonProgressiveProtocol(protocol: String): Boolean =
        protocol.contains("http_dash_segments", ignoreCase = true)

    private fun JSONObject?.optCleanString(key: String): String {
        val value = this?.optString(key).orEmpty()
        return if (value.equals("null", ignoreCase = true)) "" else value
    }

    private fun mp4FormatSelector(maxHeight: Int? = null): String {
        if (maxHeight == null) {
            return "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio[ext=m4a]/bestvideo+bestaudio/best"
        }
        val h = "[height<=$maxHeight]"
        return "bestvideo$h[ext=mp4]+bestaudio[ext=m4a]/bestvideo$h+bestaudio[ext=m4a]/bestvideo$h+bestaudio/best$h/best"
    }

    suspend fun resolveMediaStreams(mediaUrl: String, maxHeight: Int = 1080): ResolvedStreams =
        withContext(Dispatchers.IO) {
            val cacheKey = "$mediaUrl::$maxHeight"
            val cached = streamCache[cacheKey]
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                return@withContext cached.streams
            }

            val activeCookiesPath = cookiesRepository.getActiveCookiesPath()
            val request = YoutubeDLRequest(mediaUrl).apply {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--no-warnings")
                if (!activeCookiesPath.isNullOrBlank()) {
                    addOption("--cookies", activeCookiesPath)
                }
            }

            val response = YoutubeDL.getInstance().execute(request, null, null)
            val info = JSONObject(response.out)
            val formatsArr = info.optJSONArray("formats")

            data class FormatCandidate(
                val url: String,
                val ext: String,
                val vcodec: String,
                val acodec: String,
                val height: Int,
                val tbr: Double,
                val abr: Double,
                val protocol: String,
                val userAgent: String,
                val referer: String,
                val cookie: String,
                val isHlsOrDash: Boolean
            )

            val candidates = mutableListOf<FormatCandidate>()
            if (formatsArr != null) {
                for (i in 0 until formatsArr.length()) {
                    val f = formatsArr.optJSONObject(i) ?: continue
                    val u = f.optCleanString("url")
                    if (u.isBlank()) continue
                    val ext = f.optCleanString("ext").lowercase()
                    val protocol = f.optCleanString("protocol")
                    if (isSegmentedNonProgressiveProtocol(protocol)) continue
                    val headers = f.optJSONObject("http_headers")
                    candidates.add(
                        FormatCandidate(
                            url = u,
                            ext = ext,
                            vcodec = f.optString("vcodec", "none"),
                            acodec = f.optString("acodec", "none"),
                            height = f.optInt("height", 0),
                            tbr = f.optDouble("tbr", 0.0).let { if (it.isNaN()) 0.0 else it },
                            abr = f.optDouble("abr", 0.0).let { if (it.isNaN()) 0.0 else it },
                            protocol = protocol,
                            userAgent = headers.optCleanString("User-Agent"),
                            referer = headers.optCleanString("Referer"),
                            cookie = headers.optCleanString("Cookie").ifBlank { headers.optCleanString("cookie") },
                            isHlsOrDash = isHlsOrDashFormat(u, protocol, ext)
                        )
                    )
                }
            }

            fun videoCompatScore(c: FormatCandidate): Int {
                if (!isCodecSupportedByDevice(c.vcodec)) return 0
                val is10Bit = is10BitCodec(c.vcodec)
                val isHw = hasHardwareVideoDecoder(c.vcodec)
                return when {
                    isH264Codec(c.vcodec) && c.ext == "mp4" -> 6
                    isH264Codec(c.vcodec) -> 5
                    isVp9Codec(c.vcodec) && !is10Bit && isHw -> 4
                    isHevcCodec(c.vcodec) && !is10Bit && isHw -> 4
                    isVp9Codec(c.vcodec) && !is10Bit -> 3
                    isHevcCodec(c.vcodec) && !is10Bit -> 2
                    isAv1Codec(c.vcodec) && isHw && !is10Bit -> 2
                    isAv1Codec(c.vcodec) || is10Bit -> 1
                    else -> 3
                }
            }

            fun audioCompatScore(c: FormatCandidate): Int {
                if (!isCodecSupportedByDevice(c.acodec)) return 0
                return when {
                    (c.ext == "m4a" || c.ext == "mp4") && isAacOrMp4aCodec(c.acodec) -> 4
                    c.ext == "m4a" || c.ext == "mp4" -> 3
                    (c.ext == "webm" || c.ext == "opus") && isOpusCodec(c.acodec) -> 2
                    else -> 1
                }
            }

            fun formatResLabel(c: FormatCandidate): String =
                if (c.height > 0) "${c.height}p" else c.ext.uppercase().ifBlank { "auto" }

            fun codecShortName(vcodec: String): String = when {
                isH264Codec(vcodec) -> "H.264"
                isVp9Codec(vcodec) -> if (is10BitCodec(vcodec)) "VP9 10-bit" else "VP9"
                isHevcCodec(vcodec) -> "HEVC"
                isAv1Codec(vcodec) -> "AV1"
                else -> vcodec.substringBefore('.').uppercase().ifBlank { "Video" }
            }

            val nonHlsProgressiveList = candidates
                .filter {
                    !it.isHlsOrDash &&
                        hasValidCodec(it.vcodec) && hasValidCodec(it.acodec) &&
                        (it.ext == "mp4" || it.ext == "webm")
                }
                .sortedWith(
                    compareByDescending<FormatCandidate> {
                        isCodecSupportedByDevice(it.vcodec) && isCodecSupportedByDevice(it.acodec)
                    }
                        .thenByDescending { it.ext == "mp4" && isH264Codec(it.vcodec) && isAacOrMp4aCodec(it.acodec) }
                        .thenByDescending { it.ext == "mp4" }
                        .thenByDescending { videoCompatScore(it) }
                        .thenByDescending { it.height }
                        .thenByDescending { it.tbr }
                )
            val nonHlsProgressive = nonHlsProgressiveList.firstOrNull()

            val nonHlsAudioSorted = candidates
                .filter {
                    !it.isHlsOrDash &&
                        hasValidCodec(it.acodec) && !hasValidCodec(it.vcodec)
                }
                .sortedWith(
                    compareByDescending<FormatCandidate> { isCodecSupportedByDevice(it.acodec) }
                        .thenByDescending { audioCompatScore(it) }
                        .thenByDescending { maxOf(it.abr, it.tbr) }
                )

            val nonHlsM4aAudio = nonHlsAudioSorted.firstOrNull {
                (it.ext == "m4a" || it.ext == "mp4") && isCodecSupportedByDevice(it.acodec)
            } ?: nonHlsAudioSorted.firstOrNull { it.ext == "m4a" || it.ext == "mp4" }

            val nonHlsOpusAudio = nonHlsAudioSorted.firstOrNull {
                (it.ext == "webm" || it.ext == "opus" || isOpusCodec(it.acodec)) && isCodecSupportedByDevice(it.acodec)
            }
            val nonHlsBestAudio = nonHlsM4aAudio ?: nonHlsAudioSorted.firstOrNull()

            fun bestNonHlsAudioForVideo(video: FormatCandidate): FormatCandidate? =
                if (video.ext == "mp4") {
                    nonHlsM4aAudio ?: nonHlsBestAudio
                } else {
                    nonHlsM4aAudio ?: nonHlsOpusAudio ?: nonHlsBestAudio
                }

            val fastH264Mp4Videos = candidates
                .filter {
                    !it.isHlsOrDash &&
                        hasValidCodec(it.vcodec) && !hasValidCodec(it.acodec) &&
                        it.ext == "mp4" &&
                        isH264Codec(it.vcodec) &&
                        isCodecSupportedByDevice(it.vcodec) &&
                        (it.height == 0 || it.height <= 720)
                }
                .sortedWith(compareByDescending<FormatCandidate> { it.height }.thenByDescending { it.tbr })

            val fastSupportedVideos = candidates
                .filter {
                    !it.isHlsOrDash &&
                        hasValidCodec(it.vcodec) && !hasValidCodec(it.acodec) &&
                        isCodecSupportedByDevice(it.vcodec) &&
                        (it.height == 0 || it.height <= 720)
                }
                .sortedWith(
                    compareByDescending<FormatCandidate> { videoCompatScore(it) }
                        .thenByDescending { it.height }
                        .thenByDescending { it.tbr }
                )

            val hqCompatibleVideos = candidates
                .filter {
                    !it.isHlsOrDash &&
                        hasValidCodec(it.vcodec) && !hasValidCodec(it.acodec) &&
                        (it.height == 0 || it.height <= maxHeight)
                }
                .sortedWith(
                    compareByDescending<FormatCandidate> { isCodecSupportedByDevice(it.vcodec) }
                        .thenByDescending { !isAv1Codec(it.vcodec) && !is10BitCodec(it.vcodec) }
                        .thenByDescending { hasHardwareVideoDecoder(it.vcodec) }
                        .thenByDescending { it.height }
                        .thenByDescending { videoCompatScore(it) }
                        .thenByDescending { it.tbr }
                )

            val hlsCombinedStreams = candidates
                .filter {
                    it.isHlsOrDash &&
                        hasValidCodec(it.vcodec) && hasValidCodec(it.acodec) &&
                        (it.height == 0 || it.height <= maxHeight)
                }
                .sortedWith(
                    compareByDescending<FormatCandidate> { it.height <= 720 }
                        .thenByDescending { it.height }
                        .thenByDescending { it.tbr }
                )

            val hlsAudioOnlyStreams = candidates
                .filter {
                    it.isHlsOrDash &&
                        hasValidCodec(it.acodec) && !hasValidCodec(it.vcodec)
                }
                .sortedByDescending { maxOf(it.abr, it.tbr) }

            val rawFallbackUrl = info.optCleanString("url")
            val topHasVideo = hasValidCodec(info.optString("vcodec", "none"))
            val topHasAudio = hasValidCodec(info.optString("acodec", "none"))
            val topProtocol = info.optCleanString("protocol")
            val topExt = info.optCleanString("ext").lowercase()
            val topIsHlsOrDash = isHlsOrDashFormat(rawFallbackUrl, topProtocol, topExt)

            // Build ranked audioAttempts
            val audioAttemptsList = buildList {
                val seen = HashSet<String>()
                fun addAudioAttempt(label: String, url: String, isHlsOrDash: Boolean) {
                    if (url.isBlank()) return
                    val key = "$url|$isHlsOrDash"
                    if (seen.add(key)) {
                        add(
                            PlaybackAttempt(
                                label = label,
                                videoUrl = url,
                                audioUrl = "",
                                isHlsOrDash = isHlsOrDash,
                                includeSubtitles = false,
                                isAudioOnly = true
                            )
                        )
                    }
                }

                // Tier 1: Non-HLS M4A/AAC
                nonHlsAudioSorted
                    .filter { (it.ext == "m4a" || it.ext == "mp4" || isAacOrMp4aCodec(it.acodec)) }
                    .take(2)
                    .forEach { addAudioAttempt("M4A/AAC (${it.abr.toInt().takeIf { b -> b > 0 } ?: it.tbr.toInt()} kbps)", it.url, false) }

                // Tier 2: Non-HLS WebM/Opus
                nonHlsAudioSorted
                    .filter { it.ext == "webm" || it.ext == "opus" || isOpusCodec(it.acodec) }
                    .take(2)
                    .forEach { addAudioAttempt("WebM/Opus (${it.abr.toInt().takeIf { b -> b > 0 } ?: it.tbr.toInt()} kbps)", it.url, false) }

                // Tier 3: Any other non-HLS audio-only + non-HLS progressive MP4
                nonHlsAudioSorted.take(2).forEach {
                    addAudioAttempt("Audio (${it.ext.uppercase()})", it.url, false)
                }
                nonHlsProgressiveList
                    .filter { it.ext == "mp4" }
                    .take(1)
                    .forEach { addAudioAttempt("MP4 Audio (${formatResLabel(it)})", it.url, false) }

                // Tier 4: HLS audio
                hlsAudioOnlyStreams.firstOrNull()?.let {
                    addAudioAttempt("HLS Audio", it.url, true)
                }
                hlsCombinedStreams.firstOrNull()?.let {
                    addAudioAttempt("HLS Stream Audio", it.url, true)
                }

                if (isEmpty() && rawFallbackUrl.isNotBlank() && !isSegmentedNonProgressiveProtocol(topProtocol)) {
                    addAudioAttempt("Přímý audio stream", rawFallbackUrl, topIsHlsOrDash)
                }
            }

            // Build ranked fastVideoAttempts
            val fastAttemptsList = buildList {
                val seen = HashSet<String>()
                fun addFastAttempt(attempt: PlaybackAttempt) {
                    if (attempt.videoUrl.isBlank()) return
                    val key = "${attempt.videoUrl}|${attempt.audioUrl}|${attempt.isHlsOrDash}|${attempt.isAudioOnly}"
                    if (seen.add(key)) add(attempt)
                }

                // Tier 1: Non-HLS progressive MP4/WebM (avc1/mp4a preferred)
                nonHlsProgressiveList.take(2).forEach { prog ->
                    addFastAttempt(
                        PlaybackAttempt(
                            label = "Rychlý ${prog.ext.uppercase()} ${formatResLabel(prog)} (${codecShortName(prog.vcodec)})",
                            videoUrl = prog.url,
                            audioUrl = "",
                            isHlsOrDash = false
                        )
                    )
                }

                // Tier 2: Non-HLS device-compatible MP4 H.264 <=720p + non-HLS M4A/AAC audio
                val fastMp4Audio = nonHlsM4aAudio ?: nonHlsBestAudio
                if (fastMp4Audio != null) {
                    fastH264Mp4Videos.take(2).forEach { v ->
                        addFastAttempt(
                            PlaybackAttempt(
                                label = "MP4 H.264 ${formatResLabel(v)} + ${fastMp4Audio.ext.uppercase()}",
                                videoUrl = v.url,
                                audioUrl = fastMp4Audio.url,
                                isHlsOrDash = false
                            )
                        )
                    }
                }

                // Tier 3: Non-HLS any supported <=720p video + best non-HLS audio
                fastSupportedVideos.take(2).forEach { v ->
                    val pairedAudio = bestNonHlsAudioForVideo(v)
                    if (pairedAudio != null) {
                        addFastAttempt(
                            PlaybackAttempt(
                                label = "${codecShortName(v.vcodec)} ${formatResLabel(v)} + ${pairedAudio.ext.uppercase()}",
                                videoUrl = v.url,
                                audioUrl = pairedAudio.url,
                                isHlsOrDash = false
                            )
                        )
                    }
                }

                // Tier 4: Standalone HLS m3u8 stream with both audio & video
                hlsCombinedStreams.firstOrNull()?.let { hls ->
                    addFastAttempt(
                        PlaybackAttempt(
                            label = "HLS ${formatResLabel(hls)}",
                            videoUrl = hls.url,
                            audioUrl = "",
                            isHlsOrDash = true
                        )
                    )
                }

                // Tier 5: Higher/lower compatible MP4 fallback, then audio-only safe last fallback
                val fallbackH264 = candidates
                    .filter {
                        !it.isHlsOrDash &&
                            hasValidCodec(it.vcodec) && !hasValidCodec(it.acodec) &&
                            it.ext == "mp4" &&
                            isH264Codec(it.vcodec) &&
                            isCodecSupportedByDevice(it.vcodec)
                    }
                    .sortedWith(compareByDescending<FormatCandidate> { it.height }.thenByDescending { it.tbr })
                    .firstOrNull()
                if (fallbackH264 != null && fastMp4Audio != null) {
                    addFastAttempt(
                        PlaybackAttempt(
                            label = "MP4 H.264 ${formatResLabel(fallbackH264)} + ${fastMp4Audio.ext.uppercase()}",
                            videoUrl = fallbackH264.url,
                            audioUrl = fastMp4Audio.url,
                            isHlsOrDash = false
                        )
                    )
                }

                if (rawFallbackUrl.isNotBlank() && topHasVideo && !isSegmentedNonProgressiveProtocol(topProtocol)) {
                    addFastAttempt(
                        PlaybackAttempt(
                            label = "Záložní stream",
                            videoUrl = rawFallbackUrl,
                            audioUrl = "",
                            isHlsOrDash = topIsHlsOrDash
                        )
                    )
                }

                audioAttemptsList.firstOrNull()?.let { audioFallback ->
                    addFastAttempt(
                        audioFallback.copy(
                            label = "Pouze zvuk (záložní: ${audioFallback.label})",
                            includeSubtitles = false,
                            isAudioOnly = true
                        )
                    )
                }
            }

            // Build ranked hqVideoAttempts
            val hqAttemptsList = buildList {
                val seen = HashSet<String>()
                fun addHqAttempt(attempt: PlaybackAttempt) {
                    if (attempt.videoUrl.isBlank()) return
                    val key = "${attempt.videoUrl}|${attempt.audioUrl}|${attempt.isHlsOrDash}|${attempt.isAudioOnly}"
                    if (seen.add(key)) add(attempt)
                }

                // Tier 1: Non-HLS best compatible video (<= maxHeight, avoiding unsupported av01 when avc1/vp9 exists) + best non-HLS audio
                val primaryHq = hqCompatibleVideos.firstOrNull()
                if (primaryHq != null) {
                    val primaryAudio = bestNonHlsAudioForVideo(primaryHq)
                    if (primaryAudio != null) {
                        addHqAttempt(
                            PlaybackAttempt(
                                label = "HQ ${codecShortName(primaryHq.vcodec)} ${formatResLabel(primaryHq)} + ${primaryAudio.ext.uppercase()}",
                                videoUrl = primaryHq.url,
                                audioUrl = primaryAudio.url,
                                isHlsOrDash = false
                            )
                        )
                        // Also add alternate audio pairing (e.g. Opus if M4A failed or M4A if Opus failed) if distinct
                        val altAudio = if (primaryAudio == nonHlsM4aAudio) nonHlsOpusAudio else nonHlsM4aAudio
                        if (altAudio != null) {
                            addHqAttempt(
                                PlaybackAttempt(
                                    label = "HQ ${codecShortName(primaryHq.vcodec)} ${formatResLabel(primaryHq)} + ${altAudio.ext.uppercase()}",
                                    videoUrl = primaryHq.url,
                                    audioUrl = altAudio.url,
                                    isHlsOrDash = false
                                )
                            )
                        }
                    }
                }
                // Secondary HQ compatible video (e.g. best H.264 MP4 <= maxHeight if primary was VP9/HEVC/AV1)
                val bestH264Hq = hqCompatibleVideos.firstOrNull { isH264Codec(it.vcodec) && it.ext == "mp4" }
                if (bestH264Hq != null) {
                    val m4a = nonHlsM4aAudio ?: nonHlsBestAudio
                    if (m4a != null) {
                        addHqAttempt(
                            PlaybackAttempt(
                                label = "HQ MP4 H.264 ${formatResLabel(bestH264Hq)} + ${m4a.ext.uppercase()}",
                                videoUrl = bestH264Hq.url,
                                audioUrl = m4a.url,
                                isHlsOrDash = false
                            )
                        )
                    }
                }

                // Tier 2: Non-HLS <=720p H.264 MP4 + M4A/AAC audio
                val tier2Audio = nonHlsM4aAudio ?: nonHlsBestAudio
                if (tier2Audio != null) {
                    fastH264Mp4Videos.firstOrNull()?.let { v720 ->
                        addHqAttempt(
                            PlaybackAttempt(
                                label = "MP4 H.264 ${formatResLabel(v720)} + ${tier2Audio.ext.uppercase()}",
                                videoUrl = v720.url,
                                audioUrl = tier2Audio.url,
                                isHlsOrDash = false
                            )
                        )
                    }
                }

                // Tier 3: Non-HLS progressive MP4 + Standalone HLS
                nonHlsProgressiveList.firstOrNull { it.ext == "mp4" }?.let { prog ->
                    addHqAttempt(
                        PlaybackAttempt(
                            label = "MP4 ${formatResLabel(prog)} (${codecShortName(prog.vcodec)})",
                            videoUrl = prog.url,
                            audioUrl = "",
                            isHlsOrDash = false
                        )
                    )
                }
                hlsCombinedStreams.firstOrNull()?.let { hls ->
                    addHqAttempt(
                        PlaybackAttempt(
                            label = "HLS ${formatResLabel(hls)}",
                            videoUrl = hls.url,
                            audioUrl = "",
                            isHlsOrDash = true
                        )
                    )
                }

                if (rawFallbackUrl.isNotBlank() && topHasVideo && !isSegmentedNonProgressiveProtocol(topProtocol)) {
                    addHqAttempt(
                        PlaybackAttempt(
                            label = "Záložní video stream",
                            videoUrl = rawFallbackUrl,
                            audioUrl = "",
                            isHlsOrDash = topIsHlsOrDash
                        )
                    )
                }
            }

            val fastVideoOnly = fastH264Mp4Videos.firstOrNull() ?: fastSupportedVideos.firstOrNull()
            val videoOnly = hqCompatibleVideos.firstOrNull()
            val hlsProgressive = if (nonHlsProgressive == null && (videoOnly == null || nonHlsBestAudio == null)) {
                hlsCombinedStreams.firstOrNull()
            } else null

            val progressive = nonHlsProgressive ?: hlsProgressive

            val audioOnly = nonHlsBestAudio ?: if (hlsProgressive == null) {
                hlsAudioOnlyStreams.firstOrNull()
            } else null

            val topAllowedForProgressive = !topIsHlsOrDash ||
                videoOnly == null || nonHlsBestAudio == null
            val fallbackUrl = if (rawFallbackUrl.isNotBlank() && topHasVideo && topHasAudio && topAllowedForProgressive) {
                rawFallbackUrl
            } else ""

            val topHeaders = info.optJSONObject("http_headers")
            val ua = progressive?.userAgent?.ifBlank { null }
                ?: fastVideoOnly?.userAgent?.ifBlank { null }
                ?: videoOnly?.userAgent?.ifBlank { null }
                ?: audioOnly?.userAgent?.ifBlank { null }
                ?: topHeaders.optCleanString("User-Agent").ifBlank { null }
                ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
            val ref = progressive?.referer?.ifBlank { null }
                ?: fastVideoOnly?.referer?.ifBlank { null }
                ?: videoOnly?.referer?.ifBlank { null }
                ?: audioOnly?.referer?.ifBlank { null }
                ?: topHeaders.optCleanString("Referer")
            val cookieHeader = progressive?.cookie?.ifBlank { null }
                ?: fastVideoOnly?.cookie?.ifBlank { null }
                ?: videoOnly?.cookie?.ifBlank { null }
                ?: audioOnly?.cookie?.ifBlank { null }
                ?: topHeaders.optCleanString("Cookie").ifBlank { topHeaders.optCleanString("cookie") }

            val durSec = info.optDouble("duration", 0.0).let { if (it.isNaN() || it < 0) 0L else it.toLong() }

            val czechSubtitles = extractCzechSubtitles(info)
            val webpageUrl = info.optString("webpage_url").ifBlank { mediaUrl }
            val extractorKey = info.optString("extractor_key")
            val resolvedPlatform = detectPlatform(
                webpageUrl = webpageUrl,
                ieKey = extractorKey,
                isInputYtmUrl = mediaUrl.contains("music.youtube.com", ignoreCase = true)
            )

            val resolvedAudioUrl = audioOnly?.url
                ?: progressive?.url
                ?: if (rawFallbackUrl.isNotBlank() && topHasAudio) rawFallbackUrl else ""
            val resolved = ResolvedStreams(
                id = info.optString("id"),
                title = info.optString("title", "Bez názvu"),
                channel = info.optString("channel").ifBlank { info.optString("uploader", "Neznámý autor") },
                durationSec = durSec,
                durationString = info.optString("duration_string").ifBlank { formatDuration(durSec) },
                thumbnail = info.optString("thumbnail"),
                progressiveUrl = progressive?.url ?: fallbackUrl,
                videoUrl = videoOnly?.url ?: progressive?.url ?: if (rawFallbackUrl.isNotBlank() && topHasVideo) rawFallbackUrl else "",
                audioUrl = resolvedAudioUrl,
                userAgent = ua,
                referer = ref,
                czechSubtitles = czechSubtitles,
                platform = resolvedPlatform,
                fastVideoUrl = fastVideoOnly?.url ?: videoOnly?.url.orEmpty(),
                cookieHeader = cookieHeader,
                fastVideoAttempts = fastAttemptsList,
                hqVideoAttempts = hqAttemptsList,
                audioAttempts = audioAttemptsList
            )

            streamCache[cacheKey] = CachedStream(resolved, System.currentTimeMillis() + 25 * 60 * 1000L)
            resolved
        }

    private fun extractCzechSubtitles(info: JSONObject): SubtitleInfo {
        val allowedExts = setOf("vtt", "srt", "ttml")
        fun pick(map: JSONObject?, isAuto: Boolean): SubtitleInfo? {
            if (map == null) return null
            val keys = map.keys()
            val csRegex = Regex("^(cs|ces|cze)($|[-_])", RegexOption.IGNORE_CASE)
            while (keys.hasNext()) {
                val langKey = keys.next()
                if (!csRegex.containsMatchIn(langKey)) continue
                val tracks = map.optJSONArray(langKey) ?: continue
                var bestVttUrl = ""
                var bestSrtUrl = ""
                var bestTtmlUrl = ""
                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    val ext = t.optCleanString("ext").lowercase()
                    if (ext !in allowedExts) continue
                    val u = t.optCleanString("url")
                    if (u.isBlank() || !(u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true))) {
                        continue
                    }
                    when (ext) {
                        "vtt" -> if (bestVttUrl.isBlank()) bestVttUrl = u
                        "srt" -> if (bestSrtUrl.isBlank()) bestSrtUrl = u
                        "ttml" -> if (bestTtmlUrl.isBlank()) bestTtmlUrl = u
                    }
                }
                val (chosenUrl, chosenExt) = when {
                    bestVttUrl.isNotBlank() -> bestVttUrl to "vtt"
                    bestSrtUrl.isNotBlank() -> bestSrtUrl to "srt"
                    bestTtmlUrl.isNotBlank() -> bestTtmlUrl to "ttml"
                    else -> "" to ""
                }
                if (chosenUrl.isNotBlank()) {
                    return SubtitleInfo(
                        available = true,
                        isAuto = isAuto,
                        lang = langKey,
                        label = if (isAuto) "automatické" else "oficiální",
                        url = chosenUrl,
                        ext = chosenExt
                    )
                }
            }
            return null
        }

        return pick(info.optJSONObject("subtitles"), false)
            ?: pick(info.optJSONObject("automatic_captions"), true)
            ?: SubtitleInfo(available = false)
    }

    suspend fun executeDownload(
        processId: String,
        url: String,
        format: DownloadFormat,
        trimStart: String,
        trimEnd: String,
        embedSubs: Boolean,
        loudnorm: Boolean,
        onProgress: (Float, Long, String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val outDir = getDownloadsDirectory()
        val outputTemplate = "${outDir.absolutePath}/%(title).100B [%(id)s].%(ext)s"
        val activeCookiesPath = cookiesRepository.getActiveCookiesPath()

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--newline")
            addOption("-o", outputTemplate)
            if (!activeCookiesPath.isNullOrBlank()) {
                addOption("--cookies", activeCookiesPath)
            }

            when (format) {
                DownloadFormat.MP3_320 -> {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                    addOption("--embed-thumbnail")
                    addOption("--add-metadata")
                }
                DownloadFormat.MP3_192 -> {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192K")
                    addOption("--embed-thumbnail")
                    addOption("--add-metadata")
                }
                DownloadFormat.FLAC -> {
                    addOption("-x")
                    addOption("--audio-format", "flac")
                    addOption("--add-metadata")
                }
                DownloadFormat.WAV -> {
                    addOption("-x")
                    addOption("--audio-format", "wav")
                }
                DownloadFormat.M4A -> {
                    addOption("-x")
                    addOption("--audio-format", "m4a")
                    addOption("--embed-thumbnail")
                    addOption("--add-metadata")
                }
                DownloadFormat.OPUS -> {
                    addOption("-x")
                    addOption("--audio-format", "opus")
                    addOption("--add-metadata")
                }
                DownloadFormat.MP4_BEST -> {
                    addOption("-f", mp4FormatSelector())
                    addOption("--merge-output-format", "mp4")
                    addOption("--add-metadata")
                }
                DownloadFormat.MP4_1080 -> {
                    addOption("-f", mp4FormatSelector(1080))
                    addOption("--merge-output-format", "mp4")
                    addOption("--add-metadata")
                }
                DownloadFormat.MP4_720 -> {
                    addOption("-f", mp4FormatSelector(720))
                    addOption("--merge-output-format", "mp4")
                    addOption("--add-metadata")
                }
                DownloadFormat.MP4_480 -> {
                    addOption("-f", mp4FormatSelector(480))
                    addOption("--merge-output-format", "mp4")
                    addOption("--add-metadata")
                }
                DownloadFormat.MKV_BEST -> {
                    addOption("-f", "bestvideo+bestaudio/best")
                    addOption("--merge-output-format", "mkv")
                    addOption("--add-metadata")
                }
            }

            if (!format.isAudio && embedSubs) {
                addOption("--sub-langs", "cs.*,cs,ces,cze")
                addOption("--write-subs")
                addOption("--write-auto-subs")
                addOption("--embed-subs")
                addOption("--compat-options", "no-keep-subs")
            }

            if (trimStart.isNotBlank() || trimEnd.isNotBlank()) {
                val s = trimStart.trim().ifBlank { "0:00" }
                val e = trimEnd.trim().ifBlank { "inf" }
                addOption("--download-sections", "*$s-$e")
                addOption("--force-keyframes-at-cuts")
            }

            if (loudnorm) {
                addOption("--postprocessor-args", "ffmpeg:-af loudnorm=I=-16:TP=-1.5:LRA=11")
            }

            addOption("--print", "after_move:filepath")
        }

        val response = YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
            onProgress(progress, eta, line)
        }

        val pathLine = response.out.lines()
            .map { it.trim() }
            .lastOrNull { it.startsWith("/") && File(it).exists() }

        val finalFile = pathLine?.let { File(it) }
        if (finalFile != null && finalFile.exists()) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(finalFile.absolutePath),
                null,
                null
            )
        }
        finalFile
    }

    fun cancelDownload(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
        }
    }

    suspend fun listLibraryFiles(): List<LocalMediaFile> = withContext(Dispatchers.IO) {
        val dir = getDownloadsDirectory()
        val files = dir.listFiles() ?: return@withContext emptyList()
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "opus", "ogg", "aac")

        files
            .filter {
                it.isFile &&
                    !it.name.endsWith(".part") &&
                    !it.name.endsWith(".ytdl") &&
                    !it.name.endsWith(".temp")
            }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val ext = file.extension.lowercase()
                LocalMediaFile(
                    name = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    ext = ext.uppercase(),
                    isAudio = ext in audioExts
                )
            }
    }

    suspend fun deleteLibraryFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).delete()
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun formatDuration(sec: Long): String {
            if (sec <= 0L) return ""
            val s = sec % 60
            val m = (sec / 60) % 60
            val h = sec / 3600
            return if (h > 0) {
                String.format("%d:%02d:%02d", h, m, s)
            } else {
                String.format("%d:%02d", m, s)
            }
        }
    }
}
