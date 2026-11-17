package com.ffmpegplaydownloader.app.data

import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct client for YouTube Music InnerTube search API (WEB_REMIX),
 * extracting playable songs and music videos with platform & metadata tags.
 */
class YouTubeMusicClient {

    private data class RawSearchResponse(
        val items: List<MediaItem>,
        val songsChipParams: String?
    )

    suspend fun search(query: String, limit: Int = 20): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val firstPass = executeInnerTubeSearch(trimmed, params = null)
        val collected = LinkedHashMap<String, MediaItem>()
        for (item in firstPass.items) {
            collected.putIfAbsent(item.id, item)
        }

        // If unfiltered search returned fewer playable songs than requested, fetch dedicated "Skladby" (Songs) shelf.
        if (collected.size < limit) {
            val songsParams = firstPass.songsChipParams ?: DEFAULT_SONGS_PARAMS
            try {
                val songsPass = executeInnerTubeSearch(trimmed, params = songsParams)
                for (item in songsPass.items) {
                    collected.putIfAbsent(item.id, item)
                }
            } catch (_: Exception) {
            }
        }

        collected.values.take(limit)
    }

    private fun executeInnerTubeSearch(query: String, params: String?): RawSearchResponse {
        val url = URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Origin", "https://music.youtube.com")
            setRequestProperty("Referer", "https://music.youtube.com/")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36"
            )
        }

        val payload = JSONObject().apply {
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", "1.20241118.01.00")
                        .put("hl", "cs")
                        .put("gl", "CZ")
                )
            )
            put("query", query)
            if (!params.isNullOrBlank()) {
                put("params", params)
            }
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bodyText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()

            if (code !in 200..299 || bodyText.isBlank()) {
                throw IllegalStateException("YouTube Music API HTTP $code")
            }

            val root = JSONObject(bodyText)
            val sectionList = root
                .optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")

            val songsChipParams = extractSongsChipParams(sectionList?.optJSONObject("header"))
            val parsedItems = mutableListOf<MediaItem>()
            val contents = sectionList?.optJSONArray("contents")

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i) ?: continue
                    section.optJSONObject("musicCardShelfRenderer")?.let { card ->
                        parseCardShelf(card)?.let { parsedItems += it }
                        val cardSub = card.optJSONArray("contents")
                        if (cardSub != null) {
                            collectListRenderers(cardSub, parsedItems)
                        }
                    }
                    section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")?.let { shelfItems ->
                        collectListRenderers(shelfItems, parsedItems)
                    }
                    section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.let { sectionItems ->
                        collectListRenderers(sectionItems, parsedItems)
                    }
                }
            }

            return RawSearchResponse(items = parsedItems, songsChipParams = songsChipParams)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractSongsChipParams(header: JSONObject?): String? {
        val chips = header?.optJSONObject("chipCloudRenderer")?.optJSONArray("chips") ?: return null
        for (i in 0 until chips.length()) {
            val chip = chips.optJSONObject(i)?.optJSONObject("chipCloudChipRenderer") ?: continue
            val uniqueId = chip.optString("uniqueId")
            val text = runsToText(chip.optJSONObject("text")?.optJSONArray("runs"))
            if (uniqueId.equals("Skladby", true) ||
                uniqueId.equals("Songs", true) ||
                text.equals("Skladby", true) ||
                text.equals("Songs", true)
            ) {
                val p = chip.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("searchEndpoint")
                    ?.optString("params")
                if (!p.isNullOrBlank()) return p
            }
        }
        return null
    }

    private fun collectListRenderers(arr: JSONArray, out: MutableList<MediaItem>) {
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            obj.optJSONObject("musicResponsiveListItemRenderer")?.let { renderer ->
                parseListItem(renderer)?.let { out += it }
            }
        }
    }

    private fun parseCardShelf(card: JSONObject): MediaItem? {
        val watchEndpoint = card.optJSONObject("onTap")?.optJSONObject("watchEndpoint")
            ?: card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
            ?: return null

        val videoId = watchEndpoint.optString("videoId")
        if (videoId.isBlank()) return null

        val title = runsToText(card.optJSONObject("title")?.optJSONArray("runs")).ifBlank { "Bez názvu" }
        val subtitleRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
        val musicVideoType = watchEndpoint
            .optJSONObject("watchEndpointMusicSupportedConfigs")
            ?.optJSONObject("watchEndpointMusicConfig")
            ?.optString("musicVideoType")
            .orEmpty()

        val parsedMeta = parseMetaTokens(runsToTokenList(subtitleRuns), musicVideoType)
        val thumb = upgradeThumb(extractBestThumb(card.optJSONObject("thumbnail")), videoId)

        return MediaItem(
            id = videoId,
            title = title,
            url = "https://music.youtube.com/watch?v=$videoId",
            channel = parsedMeta.channel.ifBlank { "YouTube Music" },
            durationSec = parsedMeta.durationSec,
            durationString = parsedMeta.durationStr,
            viewCount = parsedMeta.viewCount,
            thumbnail = thumb,
            extractor = "YouTubeMusic",
            platform = MediaPlatform.YOUTUBE_MUSIC,
            contentKind = parsedMeta.contentKind
        )
    }

    private fun parseListItem(renderer: JSONObject): MediaItem? {
        val playWatchEndpoint = renderer
            .optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")

        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?: playWatchEndpoint?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: return null

        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
        val col0Runs = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val title = runsToText(col0Runs).ifBlank { "Bez názvu" }

        val tokens = mutableListOf<String>()
        for (idx in 1 until flexColumns.length()) {
            val colRuns = flexColumns.optJSONObject(idx)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            tokens += runsToTokenList(colRuns)
        }

        val musicVideoType = playWatchEndpoint
            ?.optJSONObject("watchEndpointMusicSupportedConfigs")
            ?.optJSONObject("watchEndpointMusicConfig")
            ?.optString("musicVideoType")
            .orEmpty()

        val parsedMeta = parseMetaTokens(tokens, musicVideoType)
        val thumb = upgradeThumb(extractBestThumb(renderer.optJSONObject("thumbnail")), videoId)

        return MediaItem(
            id = videoId,
            title = title,
            url = "https://music.youtube.com/watch?v=$videoId",
            channel = parsedMeta.channel.ifBlank { "YouTube Music" },
            durationSec = parsedMeta.durationSec,
            durationString = parsedMeta.durationStr,
            viewCount = parsedMeta.viewCount,
            thumbnail = thumb,
            extractor = "YouTubeMusic",
            platform = MediaPlatform.YOUTUBE_MUSIC,
            contentKind = parsedMeta.contentKind
        )
    }

    private data class ParsedMeta(
        val channel: String,
        val durationSec: Long,
        val durationStr: String,
        val viewCount: Long?,
        val contentKind: String
    )

    private fun parseMetaTokens(rawTokens: List<String>, musicVideoType: String): ParsedMeta {
        var detectedKind = when (musicVideoType) {
            "MUSIC_VIDEO_TYPE_ATV" -> "Skladba"
            "MUSIC_VIDEO_TYPE_OMV" -> "Hudební video"
            "MUSIC_VIDEO_TYPE_UGC" -> "Video"
            else -> ""
        }
        var durationStr = ""
        var durationSec = 0L
        var viewCount: Long? = null
        val descriptorParts = mutableListOf<String>()

        for (raw in rawTokens) {
            val token = raw.replace('\u00A0', ' ').trim()
            if (token.isEmpty() || token == "•" || token == "-") continue

            when {
                token.equals("Skladba", true) || token.equals("Song", true) -> {
                    if (detectedKind.isBlank()) detectedKind = "Skladba"
                }
                token.equals("Video", true) -> {
                    if (detectedKind.isBlank()) detectedKind = "Video"
                }
                token.equals("Epizoda", true) || token.equals("Episode", true) -> {
                    if (detectedKind.isBlank()) detectedKind = "Epizoda"
                }
                DURATION_REGEX.matches(token) -> {
                    durationStr = token
                    durationSec = parseDurationToSeconds(token)
                }
                VIEWS_REGEX.containsMatchIn(token) -> {
                    viewCount = parseApproxCount(token)
                }
                else -> {
                    descriptorParts += token
                }
            }
        }

        if (detectedKind.isBlank()) {
            detectedKind = "Skladba"
        }

        return ParsedMeta(
            channel = descriptorParts.distinct().take(2).joinToString(" • "),
            durationSec = durationSec,
            durationStr = durationStr.ifBlank { YtDlpRepository.formatDuration(durationSec) },
            viewCount = viewCount,
            contentKind = detectedKind
        )
    }

    private fun runsToText(runs: JSONArray?): String {
        if (runs == null) return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun runsToTokenList(runs: JSONArray?): List<String> {
        if (runs == null) return emptyList()
        return buildList {
            for (i in 0 until runs.length()) {
                val t = runs.optJSONObject(i)?.optString("text").orEmpty().trim()
                if (t.isNotEmpty() && t != "•") {
                    add(t)
                }
            }
        }
    }

    private fun extractBestThumb(thumbContainer: JSONObject?): String {
        val thumbs = thumbContainer
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: return ""
        var best = ""
        for (i in 0 until thumbs.length()) {
            val u = thumbs.optJSONObject(i)?.optString("url").orEmpty()
            if (u.isNotBlank()) best = u
        }
        return best
    }

    private fun upgradeThumb(rawThumb: String, videoId: String): String {
        if (rawThumb.isBlank()) {
            return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }
        return rawThumb.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
    }

    private fun parseDurationToSeconds(str: String): Long {
        val parts = str.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    private fun parseApproxCount(text: String): Long? {
        val clean = text.lowercase().replace('\u00A0', ' ')
        val numMatch = Regex("""(\d+(?:[.,]\d+)?)""").find(clean) ?: return null
        val value = numMatch.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when {
            clean.contains("mld") || clean.contains("b ") || clean.endsWith("b") -> 1_000_000_000.0
            clean.contains("mil") || clean.contains("m ") || Regex("""\d\s*m\b""").containsMatchIn(clean) -> 1_000_000.0
            clean.contains("tis") || clean.contains("k ") || Regex("""\d\s*k\b""").containsMatchIn(clean) -> 1_000.0
            else -> 1.0
        }
        return (value * multiplier).toLong().takeIf { it > 0L }
    }

    companion object {
        private const val DEFAULT_SONGS_PARAMS = "EgWKAQIIAWoKEAMQBBAJEAoQBQ=="
        private val DURATION_REGEX = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")
        private val VIEWS_REGEX = Regex("""(zhlédnutí|přehrání|views|plays)""", RegexOption.IGNORE_CASE)
    }
}
