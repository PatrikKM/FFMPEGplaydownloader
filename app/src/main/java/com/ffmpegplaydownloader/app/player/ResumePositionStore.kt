package com.ffmpegplaydownloader.app.player

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Persists playback resume positions per media key in `filesDir/resume_positions.json`.
 * Entries are capped at [MAX_ENTRIES] using most-recently-updated ordering.
 */
class ResumePositionStore(context: Context) {

    private data class Entry(
        val positionMs: Long,
        val updatedAt: Long
    )

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private var loaded = false

    fun savePosition(key: String, positionMs: Long, durationMs: Long) {
        if (key.isBlank()) return

        if (durationMs > 0L && positionMs >= (durationMs * FINISHED_THRESHOLD_RATIO).toLong()) {
            clearPosition(key)
            return
        }

        if (positionMs < MIN_RESUME_POSITION_MS) return

        synchronized(lock) {
            ensureLoadedLocked()
            val existing = entries[key]
            entries.remove(key)
            entries[key] = Entry(positionMs = positionMs, updatedAt = System.currentTimeMillis())

            while (entries.size > MAX_ENTRIES) {
                val eldestKey = entries.keys.firstOrNull() ?: break
                entries.remove(eldestKey)
            }

            if (existing?.positionMs != positionMs) {
                persistLocked()
            }
        }
    }

    fun getResumePosition(key: String): Long {
        if (key.isBlank()) return 0L
        synchronized(lock) {
            ensureLoadedLocked()
            return entries[key]?.positionMs ?: 0L
        }
    }

    fun clearPosition(key: String) {
        if (key.isBlank()) return
        synchronized(lock) {
            ensureLoadedLocked()
            if (entries.remove(key) != null) {
                persistLocked()
            }
        }
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        if (!file.exists()) return

        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val parsed = mutableListOf<Pair<String, Entry>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.isBlank()) continue
                val obj = root.optJSONObject(key)
                val entry = if (obj != null) {
                    val pos = obj.optLong("positionMs", 0L)
                    val updatedAt = obj.optLong("updatedAt", 0L)
                    Entry(positionMs = pos, updatedAt = updatedAt)
                } else {
                    val pos = root.optLong(key, 0L)
                    Entry(positionMs = pos, updatedAt = 0L)
                }
                if (entry.positionMs >= MIN_RESUME_POSITION_MS) {
                    parsed.add(key to entry)
                }
            }
            parsed.sortBy { it.second.updatedAt }
            val startIndex = (parsed.size - MAX_ENTRIES).coerceAtLeast(0)
            for (i in startIndex until parsed.size) {
                val (k, v) = parsed[i]
                entries[k] = v
            }
        } catch (_: Exception) {
            entries.clear()
        }
    }

    private fun persistLocked() {
        try {
            val root = JSONObject()
            for ((key, entry) in entries) {
                val item = JSONObject().apply {
                    put("positionMs", entry.positionMs)
                    put("updatedAt", entry.updatedAt)
                }
                root.put(key, item)
            }
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val FILE_NAME = "resume_positions.json"
        private const val MAX_ENTRIES = 200
        private const val MIN_RESUME_POSITION_MS = 5_000L
        private const val FINISHED_THRESHOLD_RATIO = 0.94
    }
}
