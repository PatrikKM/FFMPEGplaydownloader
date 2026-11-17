package com.ffmpegplaydownloader.app.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.ffmpegplaydownloader.app.model.DownloadFormat
import com.ffmpegplaydownloader.app.model.LocalMediaFile
import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
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
                val referer: String
            )

            val candidates = mutableListOf<FormatCandidate>()
            if (formatsArr != null) {
                for (i in 0 until formatsArr.length()) {
                    val f = formatsArr.optJSONObject(i) ?: continue
                    val u = f.optString("url")
                    if (u.isBlank()) continue
                    val headers = f.optJSONObject("http_headers")
                    candidates.add(
                        FormatCandidate(
                            url = u,
                            ext = f.optString("ext"),
                            vcodec = f.optString("vcodec", "none"),
                            acodec = f.optString("acodec", "none"),
                            height = f.optInt("height", 0),
                            tbr = f.optDouble("tbr", 0.0).let { if (it.isNaN()) 0.0 else it },
                            abr = f.optDouble("abr", 0.0).let { if (it.isNaN()) 0.0 else it },
                            protocol = f.optString("protocol"),
                            userAgent = headers?.optString("User-Agent").orEmpty(),
                            referer = headers?.optString("Referer").orEmpty()
                        )
                    )
                }
            }

            val progressive = candidates
                .filter {
                    it.vcodec != "none" && it.acodec != "none" &&
                        (it.ext == "mp4" || it.ext == "webm")
                }
                .sortedWith(compareByDescending<FormatCandidate> { it.height }.thenByDescending { it.tbr })
                .firstOrNull()

            val videoOnly = candidates
                .filter {
                    it.vcodec != "none" &&
                        (it.height == 0 || it.height <= maxHeight) &&
                        !it.protocol.contains("m3u8_native")
                }
                .sortedWith(compareByDescending<FormatCandidate> { it.height }.thenByDescending { it.tbr })
                .firstOrNull()

            val audioCandidates = candidates
                .filter { it.acodec != "none" && it.vcodec == "none" }
                .sortedWith(compareByDescending<FormatCandidate> { maxOf(it.abr, it.tbr) })
            val audioOnly = audioCandidates
                .firstOrNull { !it.protocol.contains("m3u8") }
                ?: audioCandidates.firstOrNull()

            val fallbackUrl = info.optString("url")
            val topHeaders = info.optJSONObject("http_headers")
            val ua = progressive?.userAgent?.ifBlank { null }
                ?: audioOnly?.userAgent?.ifBlank { null }
                ?: topHeaders?.optString("User-Agent")?.ifBlank { null }
                ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
            val ref = progressive?.referer?.ifBlank { null }
                ?: audioOnly?.referer?.ifBlank { null }
                ?: topHeaders?.optString("Referer").orEmpty()

            val durSec = info.optDouble("duration", 0.0).let { if (it.isNaN() || it < 0) 0L else it.toLong() }

            val czechSubtitles = extractCzechSubtitles(info)
            val webpageUrl = info.optString("webpage_url").ifBlank { mediaUrl }
            val extractorKey = info.optString("extractor_key")
            val resolvedPlatform = detectPlatform(
                webpageUrl = webpageUrl,
                ieKey = extractorKey,
                isInputYtmUrl = mediaUrl.contains("music.youtube.com", ignoreCase = true)
            )

            val resolved = ResolvedStreams(
                id = info.optString("id"),
                title = info.optString("title", "Bez názvu"),
                channel = info.optString("channel").ifBlank { info.optString("uploader", "Neznámý autor") },
                durationSec = durSec,
                durationString = info.optString("duration_string").ifBlank { formatDuration(durSec) },
                thumbnail = info.optString("thumbnail"),
                progressiveUrl = progressive?.url ?: fallbackUrl,
                videoUrl = videoOnly?.url ?: progressive?.url ?: fallbackUrl,
                audioUrl = audioOnly?.url ?: progressive?.url ?: fallbackUrl,
                userAgent = ua,
                referer = ref,
                czechSubtitles = czechSubtitles,
                platform = resolvedPlatform
            )

            streamCache[cacheKey] = CachedStream(resolved, System.currentTimeMillis() + 25 * 60 * 1000L)
            resolved
        }

    private fun extractCzechSubtitles(info: JSONObject): SubtitleInfo {
        fun pick(map: JSONObject?, isAuto: Boolean): SubtitleInfo? {
            if (map == null) return null
            val keys = map.keys()
            val csRegex = Regex("^(cs|ces|cze)($|[-_])", RegexOption.IGNORE_CASE)
            while (keys.hasNext()) {
                val langKey = keys.next()
                if (!csRegex.containsMatchIn(langKey)) continue
                val tracks = map.optJSONArray(langKey) ?: continue
                var chosenUrl = ""
                var chosenExt = "vtt"
                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    val ext = t.optString("ext")
                    val u = t.optString("url")
                    if (u.isNotBlank() && ext == "vtt") {
                        chosenUrl = u
                        chosenExt = "vtt"
                        break
                    } else if (u.isNotBlank() && chosenUrl.isBlank()) {
                        chosenUrl = u
                        chosenExt = ext.ifBlank { "vtt" }
                    }
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
                    addOption("-f", "bestvideo+bestaudio/best")
                    addOption("--merge-output-format", "mp4")
                    addOption("--add-metadata")
                }
                DownloadFormat.MP4_1080 -> {
                    addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
                    addOption("--merge-output-format", "mp4")
                    addOption("--add-metadata")
                }
                DownloadFormat.MP4_720 -> {
                    addOption("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]/best")
                    addOption("--merge-output-format", "mp4")
                    addOption("--add-metadata")
                }
                DownloadFormat.MP4_480 -> {
                    addOption("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]/best")
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
