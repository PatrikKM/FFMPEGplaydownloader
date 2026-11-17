package com.ffmpegplaydownloader.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

data class CookiesStatus(
    val hasFile: Boolean = false,
    val enabled: Boolean = false,
    val fileName: String = "",
    val entryCount: Int = 0,
    val importedAt: Long = 0L
)

class CookiesRepository(private val context: Context) {

    private val cookiesFile = File(context.filesDir, COOKIES_FILE_NAME)
    private val configFile = File(context.filesDir, CONFIG_FILE_NAME)
    private val lock = Any()

    fun importFromUri(uri: Uri): Result<CookiesStatus> {
        val rawText = readUtf8WithLimit(uri).getOrElse { return Result.failure(it) }
        val normalized = rawText.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Vybraný soubor je prázdný."))
        }

        val parsed = parseAndValidateNetscapeCookies(normalized).getOrElse { return Result.failure(it) }
        val displayName = resolveDisplayName(uri)
        val now = System.currentTimeMillis()

        return synchronized(lock) {
            try {
                cookiesFile.parentFile?.mkdirs()
                cookiesFile.writeText(parsed.contentToWrite, Charsets.UTF_8)
                val status = CookiesStatus(
                    hasFile = true,
                    enabled = true,
                    fileName = displayName,
                    entryCount = parsed.entryCount,
                    importedAt = now
                )
                saveStatusLocked(status)
                Result.success(status)
            } catch (e: Exception) {
                Result.failure(
                    IllegalStateException(
                        "Nepodařilo se uložit soubor cookies: ${e.message ?: "neznámá chyba"}",
                        e
                    )
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean): CookiesStatus = synchronized(lock) {
        val current = loadStatusLocked()
        if (!current.hasFile) {
            return@synchronized current
        }
        val updated = current.copy(enabled = enabled)
        saveStatusLocked(updated)
        updated
    }

    fun clearCookies(): CookiesStatus = synchronized(lock) {
        runCatching {
            if (cookiesFile.exists()) {
                cookiesFile.delete()
            }
        }
        val empty = CookiesStatus()
        saveStatusLocked(empty)
        empty
    }

    fun getStatus(): CookiesStatus = synchronized(lock) {
        loadStatusLocked()
    }

    fun getActiveCookiesPath(): String? = synchronized(lock) {
        val status = loadStatusLocked()
        if (status.hasFile && status.enabled && cookiesFile.exists() && cookiesFile.isFile && cookiesFile.length() > 0L) {
            cookiesFile.absolutePath
        } else {
            null
        }
    }

    private fun readUtf8WithLimit(uri: Uri): Result<String> {
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            return Result.failure(
                IllegalArgumentException("Nepodařilo se otevřít vybraný soubor: ${e.message ?: "neznámá chyba"}")
            )
        } ?: return Result.failure(IllegalArgumentException("Nepodařilo se otevřít vybraný soubor."))

        return inputStream.use { stream ->
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_COOKIE_FILE_BYTES) {
                    return@use Result.failure(
                        IllegalArgumentException(
                            "Soubor cookies.txt je příliš velký (maximální povolená velikost je 5 MB)."
                        )
                    )
                }
                out.write(buffer, 0, read)
            }
            val bytes = out.toByteArray()
            if (bytes.isEmpty()) {
                return@use Result.failure(IllegalArgumentException("Vybraný soubor je prázdný."))
            }
            if (bytes.any { it == 0.toByte() }) {
                return@use Result.failure(
                    IllegalArgumentException("Soubor není platný textový soubor Netscape cookies.txt.")
                )
            }
            Result.success(bytes.toString(Charsets.UTF_8))
        }
    }

    private data class ParsedCookies(
        val contentToWrite: String,
        val entryCount: Int
    )

    private fun parseAndValidateNetscapeCookies(text: String): Result<ParsedCookies> {
        val lines = text.lines()
        var hasNetscapeHeader = false
        var validCookieCount = 0
        val outputLines = mutableListOf<String>()

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                continue
            }

            val isHttpOnly = trimmed.startsWith(HTTP_ONLY_PREFIX, ignoreCase = true)
            if (trimmed.startsWith("#") && !isHttpOnly) {
                if (trimmed.equals("# Netscape HTTP Cookie File", ignoreCase = true) ||
                    trimmed.equals("# HTTP Cookie File", ignoreCase = true)
                ) {
                    hasNetscapeHeader = true
                }
                outputLines.add(trimmed)
                continue
            }

            val effectiveLine = if (isHttpOnly) {
                trimmed.substring(HTTP_ONLY_PREFIX.length)
            } else {
                rawLine
            }

            val columns = effectiveLine.split('\t')
            if (columns.size != 7) {
                continue
            }

            val domain = columns[0].trim()
            val includeSubdomains = columns[1].trim()
            val path = columns[2].trim()
            val secure = columns[3].trim()
            val expiry = columns[4].trim()
            val name = columns[5].trim()

            val validFlags =
                (includeSubdomains.equals("TRUE", ignoreCase = true) ||
                    includeSubdomains.equals("FALSE", ignoreCase = true)) &&
                    (secure.equals("TRUE", ignoreCase = true) ||
                        secure.equals("FALSE", ignoreCase = true))
            val validExpiry = expiry.toLongOrNull() != null ||
                expiry.toDoubleOrNull()?.let { !it.isNaN() && !it.isInfinite() && it >= 0.0 } == true

            if (domain.isNotEmpty() && path.isNotEmpty() && validFlags && validExpiry && name.isNotEmpty()) {
                validCookieCount++
                outputLines.add(
                    if (isHttpOnly) "$HTTP_ONLY_PREFIX$effectiveLine" else rawLine
                )
            }
        }

        if (validCookieCount == 0) {
            val msg = if (hasNetscapeHeader) {
                "Soubor cookies.txt má hlavičku, ale neobsahuje žádné platné záznamy cookies (7 sloupců oddělených tabulátorem)."
            } else {
                "Neplatný formát souboru. Vyberte prosím textový soubor ve formátu Netscape cookies.txt (7 sloupců oddělených tabulátorem)."
            }
            return Result.failure(IllegalArgumentException(msg))
        }

        val finalContent = buildString {
            if (!hasNetscapeHeader) {
                append("# Netscape HTTP Cookie File\n")
            }
            outputLines.forEach { line ->
                append(line)
                append('\n')
            }
        }

        return Result.success(ParsedCookies(contentToWrite = finalContent, entryCount = validCookieCount))
    }

    private fun resolveDisplayName(uri: Uri): String {
        val queried = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    cursor.getString(idx)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }.getOrNull()

        return queried
            ?: uri.takeIf { it.scheme == "file" }?.path?.let { File(it).name }?.takeIf { it.isNotBlank() }
            ?: DEFAULT_DISPLAY_NAME
    }

    private fun loadStatusLocked(): CookiesStatus {
        val fileValid = cookiesFile.exists() && cookiesFile.isFile && cookiesFile.length() > 0L
        if (!fileValid || !configFile.exists()) {
            return CookiesStatus()
        }
        return try {
            val obj = JSONObject(configFile.readText(Charsets.UTF_8))
            if (!obj.optBoolean("hasFile", false)) {
                CookiesStatus()
            } else {
                CookiesStatus(
                    hasFile = true,
                    enabled = obj.optBoolean("enabled", false),
                    fileName = obj.optString("fileName", DEFAULT_DISPLAY_NAME).ifBlank { DEFAULT_DISPLAY_NAME },
                    entryCount = obj.optInt("entryCount", 0).coerceAtLeast(0),
                    importedAt = obj.optLong("importedAt", 0L).coerceAtLeast(0L)
                )
            }
        } catch (_: Exception) {
            CookiesStatus()
        }
    }

    private fun saveStatusLocked(status: CookiesStatus) {
        try {
            val obj = JSONObject().apply {
                put("hasFile", status.hasFile)
                put("enabled", status.enabled)
                put("fileName", status.fileName)
                put("entryCount", status.entryCount)
                put("importedAt", status.importedAt)
            }
            configFile.writeText(obj.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val COOKIES_FILE_NAME = "user_cookies.txt"
        private const val CONFIG_FILE_NAME = "cookies_config.json"
        private const val DEFAULT_DISPLAY_NAME = "cookies.txt"
        private const val HTTP_ONLY_PREFIX = "#HttpOnly_"
        private const val MAX_COOKIE_FILE_BYTES = 5 * 1024 * 1024
    }
}
