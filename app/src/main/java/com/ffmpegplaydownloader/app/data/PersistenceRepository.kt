package com.ffmpegplaydownloader.app.data

import android.content.Context
import com.ffmpegplaydownloader.app.model.EqualizerBand
import com.ffmpegplaydownloader.app.model.EqualizerPreset
import com.ffmpegplaydownloader.app.model.EqualizerState
import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PersistenceRepository(private val context: Context) {

    private val queueFile = File(context.filesDir, "queue.json")
    private val favoritesFile = File(context.filesDir, "favorites.json")
    private val historyFile = File(context.filesDir, "history.json")
    private val equalizerFile = File(context.filesDir, "equalizer.json")

    fun loadQueue(): List<MediaItem> = loadList(queueFile)
    fun saveQueue(items: List<MediaItem>) = saveList(queueFile, items)

    fun loadFavorites(): List<MediaItem> = loadList(favoritesFile)
    fun saveFavorites(items: List<MediaItem>) = saveList(favoritesFile, items)

    fun loadHistory(): List<MediaItem> = loadList(historyFile)
    fun saveHistory(items: List<MediaItem>) = saveList(historyFile, items.take(100))

    private fun loadList(file: File): List<MediaItem> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val url = obj.optString("url")
                    val extractor = obj.optString("extractor", "Web")
                    val isLocal = obj.optBoolean("isLocal", false)
                    val platformName = obj.optString("platform")
                    val platform = runCatching {
                        if (platformName.isNotBlank()) MediaPlatform.valueOf(platformName) else null
                    }.getOrNull() ?: inferPlatform(isLocal, url, extractor)

                    add(
                        MediaItem(
                            id = obj.optString("id"),
                            title = obj.optString("title", "Bez názvu"),
                            url = url,
                            channel = obj.optString("channel"),
                            durationSec = obj.optLong("durationSec", 0L),
                            durationString = obj.optString("durationString"),
                            viewCount = if (obj.has("viewCount") && !obj.isNull("viewCount")) obj.optLong("viewCount") else null,
                            thumbnail = obj.optString("thumbnail"),
                            extractor = extractor,
                            platform = platform,
                            contentKind = obj.optString("contentKind"),
                            isLocal = isLocal,
                            localPath = obj.optString("localPath"),
                            ext = obj.optString("ext"),
                            addedAt = obj.optLong("addedAt", 0L),
                            playedAt = obj.optLong("playedAt", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun inferPlatform(isLocal: Boolean, url: String, extractor: String): MediaPlatform = when {
        isLocal -> MediaPlatform.LOCAL
        url.contains("music.youtube.com", ignoreCase = true) ||
            extractor.contains("YouTubeMusic", ignoreCase = true) -> MediaPlatform.YOUTUBE_MUSIC
        url.contains("youtube.com", ignoreCase = true) ||
            url.contains("youtu.be", ignoreCase = true) ||
            extractor.equals("Youtube", ignoreCase = true) ||
            extractor.equals("YouTube", ignoreCase = true) -> MediaPlatform.YOUTUBE
        else -> MediaPlatform.OTHER
    }

    private fun saveList(file: File, items: List<MediaItem>) {
        try {
            val arr = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("url", item.url)
                    put("channel", item.channel)
                    put("durationSec", item.durationSec)
                    put("durationString", item.durationString)
                    if (item.viewCount != null) put("viewCount", item.viewCount)
                    put("thumbnail", item.thumbnail)
                    put("extractor", item.extractor)
                    put("platform", item.platform.name)
                    put("contentKind", item.contentKind)
                    put("isLocal", item.isLocal)
                    put("localPath", item.localPath)
                    put("ext", item.ext)
                    put("addedAt", item.addedAt)
                    put("playedAt", item.playedAt)
                }
                arr.put(obj)
            }
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun loadEqualizerState(): EqualizerState {
        if (!equalizerFile.exists()) return EqualizerState()
        return try {
            val obj = JSONObject(equalizerFile.readText(Charsets.UTF_8))
            val preset = runCatching {
                EqualizerPreset.valueOf(obj.optString("preset", EqualizerPreset.FLAT.name))
            }.getOrDefault(EqualizerPreset.FLAT)
            val defaults = EqualizerState.defaultBands()
            val levelsArr = obj.optJSONArray("bandLevelsMb")
            val bands = defaults.mapIndexed { idx, band ->
                val lvl = if (levelsArr != null && idx < levelsArr.length()) levelsArr.optInt(idx, 0) else 0
                band.copy(levelMb = lvl)
            }
            EqualizerState(
                enabled = obj.optBoolean("enabled", false),
                preset = preset,
                bassBoostStrength = obj.optInt("bassBoostStrength", 0).coerceIn(0, 1000),
                loudnessEnabled = obj.optBoolean("loudnessEnabled", false),
                bands = bands
            )
        } catch (_: Exception) {
            EqualizerState()
        }
    }

    fun saveEqualizerState(state: EqualizerState) {
        try {
            val obj = JSONObject().apply {
                put("enabled", state.enabled)
                put("preset", state.preset.name)
                put("bassBoostStrength", state.bassBoostStrength)
                put("loudnessEnabled", state.loudnessEnabled)
                put("bandLevelsMb", JSONArray(state.bands.map { it.levelMb }))
            }
            equalizerFile.writeText(obj.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
