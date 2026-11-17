package com.ffmpegplaydownloader.app.data

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import com.ffmpegplaydownloader.app.model.LocalMediaFile
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class LocalFfmpegAction(val label: String) {
    EXTRACT_MP3_320("🎵 Extrahovat MP3 (320 kbps)"),
    EXTRACT_M4A("🎵 Extrahovat M4A (AAC)"),
    TRIM_CLIP("✂️ Oříznout Od–Do"),
    NORMALIZE_LOUDNESS("🔊 Normalizovat hlasitost (Loudnorm)"),
    COMPRESS_720P("📦 Zmenšit video na 720p (MP4)")
}

data class FfmpegBinaryInfo(
    val ffmpegBinary: File,
    val nativeLibDir: File,
    val ffmpegUsrLibDir: File
)

class LocalFfmpegProcessor(
    private val context: Context,
    private val ytDlpRepository: YtDlpRepository = YtDlpRepository(context)
) {
    private val activeProcesses = ConcurrentHashMap<String, Process>()

    /**
     * Ensures [FFmpeg] has unpacked its shared libraries (`libffmpeg.zip.so` ->
     * `noBackupFilesDir/youtubedl-android/packages/ffmpeg/usr/lib`) and resolves the
     * executable `libffmpeg.so` inside `applicationInfo.nativeLibraryDir`.
     */
    fun resolveFfmpegBinary(): FfmpegBinaryInfo {
        val appContext = context.applicationContext
        FFmpeg.getInstance().init(appContext)

        val nativeLibDir = File(appContext.applicationInfo.nativeLibraryDir)
        val ffmpegBinary = File(nativeLibDir, FFMPEG_BIN_NAME)
        val ffmpegUsrLibDir = File(
            appContext.noBackupFilesDir,
            "$YOUTUBEDL_BASE_DIR/$PACKAGES_DIR/$FFMPEG_PACKAGE_DIR/usr/lib"
        )

        if (!ffmpegBinary.exists()) {
            throw IllegalStateException(
                "Nativní binárka $FFMPEG_BIN_NAME nebyla nalezena v ${nativeLibDir.absolutePath}"
            )
        }
        return FfmpegBinaryInfo(
            ffmpegBinary = ffmpegBinary,
            nativeLibDir = nativeLibDir,
            ffmpegUsrLibDir = ffmpegUsrLibDir
        )
    }

    /**
     * Executes a local FFmpeg conversion/processing action on [inputFile] on [Dispatchers.IO],
     * writing the output into [YtDlpRepository.getDownloadsDirectory] and scanning it into MediaStore.
     *
     * @param onProgress Receives normalized progress in `0.0f..1.0f` and a localized Czech status string.
     */
    suspend fun processFile(
        inputFile: LocalMediaFile,
        action: LocalFfmpegAction,
        trimStart: String = "",
        trimEnd: String = "",
        jobId: String = UUID.randomUUID().toString().take(8),
        onProgress: (progress: Float, statusMessage: String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        var outputFile: File? = null
        var process: Process? = null
        try {
            val sourceFile = File(inputFile.path)
            require(sourceFile.exists() && sourceFile.isFile) {
                "Zdrojový soubor neexistuje: ${inputFile.name}"
            }
            if (action == LocalFfmpegAction.COMPRESS_720P && inputFile.isAudio) {
                throw IllegalArgumentException("Komprese videa na 720p je dostupná pouze pro video soubory.")
            }

            val startSec = if (action == LocalFfmpegAction.TRIM_CLIP) {
                parseTimestampToSeconds(trimStart)
            } else null
            val endSec = if (action == LocalFfmpegAction.TRIM_CLIP) {
                parseTimestampToSeconds(trimEnd)
            } else null

            if (action == LocalFfmpegAction.TRIM_CLIP) {
                if (startSec == null && endSec == null) {
                    throw IllegalArgumentException("Zadejte alespoň čas Od nebo Do (např. 0:30).")
                }
                if (startSec != null && endSec != null && endSec <= startSec) {
                    throw IllegalArgumentException("Čas Do musí být větší než čas Od.")
                }
            }

            onProgress(0.0f, "Připravuji lokální FFmpeg...")
            val binaryInfo = resolveFfmpegBinary()
            val outDir = ytDlpRepository.getDownloadsDirectory()
            val (suffix, outExt) = resolveOutputNaming(sourceFile, inputFile.isAudio, action)
            outputFile = createUniqueOutputFile(
                outDir = outDir,
                baseName = sourceFile.nameWithoutExtension.ifBlank { "media" },
                suffix = suffix,
                extension = outExt
            )

            val command = buildFfmpegCommand(
                ffmpegPath = binaryInfo.ffmpegBinary.absolutePath,
                sourceFile = sourceFile,
                outputFile = outputFile,
                isAudioInput = inputFile.isAudio,
                outExt = outExt,
                action = action,
                startSec = startSec,
                endSec = endSec
            )

            val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
            processBuilder.environment().apply {
                val existingLd = System.getenv("LD_LIBRARY_PATH")
                val ldParts = buildList {
                    if (binaryInfo.ffmpegUsrLibDir.exists()) add(binaryInfo.ffmpegUsrLibDir.absolutePath)
                    add(binaryInfo.nativeLibDir.absolutePath)
                    if (!existingLd.isNullOrBlank()) add(existingLd)
                }
                this["LD_LIBRARY_PATH"] = ldParts.joinToString(":")
                val existingPath = System.getenv("PATH") ?: "/system/bin"
                this["PATH"] = "$existingPath:${binaryInfo.nativeLibDir.absolutePath}"
                this["TMPDIR"] = context.cacheDir.absolutePath
                this["HOME"] = context.noBackupFilesDir.absolutePath
            }

            onProgress(0.02f, "Spouštím FFmpeg...")
            process = processBuilder.start()
            activeProcesses[jobId] = process

            val tailLines = ArrayDeque<String>(MAX_ERROR_LINES)
            var inputDurationSec: Double? = null
            var lastReportedProgress = 0.02f
            var lastReportTimeMs = 0L

            InputStreamReader(process.inputStream, StandardCharsets.UTF_8).use { reader ->
                val lineBuilder = StringBuilder()
                var ch = reader.read()
                while (ch != -1) {
                    currentCoroutineContext().ensureActive()
                    if (ch == '\r'.code || ch == '\n'.code) {
                        if (lineBuilder.isNotEmpty()) {
                            val line = lineBuilder.toString()
                            lineBuilder.setLength(0)

                            if (tailLines.size >= MAX_ERROR_LINES) tailLines.removeFirst()
                            tailLines.addLast(line)

                            if (inputDurationSec == null) {
                                inputDurationSec = parseDurationSeconds(line)
                            }

                            val currentTimeSec = parseProgressTimeSeconds(line)
                            if (currentTimeSec != null) {
                                val expectedTotal = computeExpectedDuration(
                                    action = action,
                                    inputDurationSec = inputDurationSec,
                                    startSec = startSec,
                                    endSec = endSec
                                )
                                val computed = if (expectedTotal != null && expectedTotal > 0.0) {
                                    (currentTimeSec / expectedTotal).toFloat().coerceIn(0.03f, 0.99f)
                                } else {
                                    0.5f
                                }
                                val now = System.currentTimeMillis()
                                if (computed - lastReportedProgress >= 0.01f || now - lastReportTimeMs >= 250L) {
                                    lastReportedProgress = computed
                                    lastReportTimeMs = now
                                    val pct = (computed * 100).toInt().coerceIn(1, 99)
                                    val speedSuffix = SPEED_REGEX.find(line)?.groupValues?.getOrNull(1)
                                        ?.let { " ($it)" }
                                        .orEmpty()
                                    onProgress(computed, "Zpracovávám: $pct %$speedSuffix")
                                }
                            }
                        }
                    } else {
                        lineBuilder.append(ch.toChar())
                    }
                    ch = reader.read()
                }
                if (lineBuilder.isNotEmpty()) {
                    val lastLine = lineBuilder.toString()
                    if (tailLines.size >= MAX_ERROR_LINES) tailLines.removeFirst()
                    tailLines.addLast(lastLine)
                }
            }

            val exitCode = process.waitFor()
            if (exitCode != 0 || !outputFile.exists() || outputFile.length() <= 0L) {
                outputFile.delete()
                val errSummary = tailLines
                    .filter { it.isNotBlank() }
                    .takeLast(4)
                    .joinToString(" | ")
                    .ifBlank { "FFmpeg skončil s kódem $exitCode" }
                throw IllegalStateException(errSummary.take(220))
            }

            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(outputFile.absolutePath),
                null,
                null
            )
            onProgress(1.0f, "Hotovo: ${outputFile.name}")
            Result.success(outputFile)
        } catch (ce: CancellationException) {
            outputFile?.delete()
            throw ce
        } catch (e: Exception) {
            outputFile?.delete()
            Result.failure(e)
        } finally {
            activeProcesses.remove(jobId)
            process?.let { proc ->
                if (isProcessAlive(proc)) {
                    destroyProcessSafely(proc)
                }
            }
        }
    }

    fun cancelJob(jobId: String): Boolean {
        val proc = activeProcesses.remove(jobId) ?: return false
        destroyProcessSafely(proc)
        return true
    }

    fun cancelAll() {
        val keys = activeProcesses.keys.toList()
        keys.forEach { cancelJob(it) }
    }

    private fun resolveOutputNaming(
        sourceFile: File,
        isAudioInput: Boolean,
        action: LocalFfmpegAction
    ): Pair<String, String> {
        val originalExt = sourceFile.extension.lowercase(Locale.ROOT)
            .ifBlank { if (isAudioInput) "m4a" else "mp4" }
        return when (action) {
            LocalFfmpegAction.EXTRACT_MP3_320 -> "_320k" to "mp3"
            LocalFfmpegAction.EXTRACT_M4A -> "_audio" to "m4a"
            LocalFfmpegAction.TRIM_CLIP -> "_trim" to originalExt
            LocalFfmpegAction.NORMALIZE_LOUDNESS -> "_loudnorm" to originalExt
            LocalFfmpegAction.COMPRESS_720P -> "_720p" to "mp4"
        }
    }

    private fun createUniqueOutputFile(
        outDir: File,
        baseName: String,
        suffix: String,
        extension: String
    ): File {
        var candidate = File(outDir, "$baseName$suffix.$extension")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(outDir, "${baseName}${suffix}_$counter.$extension")
            counter++
        }
        return candidate
    }

    private fun buildFfmpegCommand(
        ffmpegPath: String,
        sourceFile: File,
        outputFile: File,
        isAudioInput: Boolean,
        outExt: String,
        action: LocalFfmpegAction,
        startSec: Double?,
        endSec: Double?
    ): List<String> = buildList {
        add(ffmpegPath)
        add("-hide_banner")
        add("-y")

        if (action == LocalFfmpegAction.TRIM_CLIP && startSec != null) {
            add("-ss")
            add(formatFfmpegSeconds(startSec))
        }

        add("-i")
        add(sourceFile.absolutePath)

        when (action) {
            LocalFfmpegAction.EXTRACT_MP3_320 -> {
                add("-vn")
                add("-c:a")
                add("libmp3lame")
                add("-b:a")
                add("320k")
                add("-id3v2_version")
                add("3")
            }

            LocalFfmpegAction.EXTRACT_M4A -> {
                add("-vn")
                add("-c:a")
                add("aac")
                add("-b:a")
                add("256k")
                add("-movflags")
                add("+faststart")
            }

            LocalFfmpegAction.TRIM_CLIP -> {
                if (endSec != null) {
                    val durationSec = endSec - (startSec ?: 0.0)
                    add("-t")
                    add(formatFfmpegSeconds(durationSec))
                }
                add("-c")
                add("copy")
                add("-avoid_negative_ts")
                add("make_zero")
                if (outExt in MP4_FAMILY_EXTS) {
                    add("-movflags")
                    add("+faststart")
                }
            }

            LocalFfmpegAction.NORMALIZE_LOUDNESS -> {
                if (isAudioInput) {
                    add("-vn")
                } else {
                    add("-c:v")
                    add("copy")
                }
                add("-af")
                add("loudnorm=I=-16:TP=-1.5:LRA=11")
                appendLoudnormAudioCodecArgs(this, outExt)
            }

            LocalFfmpegAction.COMPRESS_720P -> {
                add("-vf")
                add("scale='trunc(iw*min(1,720/ih)/2)*2':'trunc(ih*min(1,720/ih)/2)*2'")
                add("-c:v")
                add("libx264")
                add("-preset")
                add("veryfast")
                add("-crf")
                add("24")
                add("-pix_fmt")
                add("yuv420p")
                add("-c:a")
                add("aac")
                add("-b:a")
                add("128k")
                add("-movflags")
                add("+faststart")
            }
        }

        add(outputFile.absolutePath)
    }

    private fun appendLoudnormAudioCodecArgs(args: MutableList<String>, outExt: String) {
        when (outExt) {
            "mp3" -> {
                args.add("-c:a"); args.add("libmp3lame")
                args.add("-b:a"); args.add("320k")
                args.add("-id3v2_version"); args.add("3")
            }
            "m4a", "aac" -> {
                args.add("-c:a"); args.add("aac")
                args.add("-b:a"); args.add("256k")
                args.add("-movflags"); args.add("+faststart")
            }
            "flac" -> {
                args.add("-c:a"); args.add("flac")
            }
            "wav" -> {
                args.add("-c:a"); args.add("pcm_s16le")
            }
            "opus", "ogg", "webm" -> {
                args.add("-c:a"); args.add("libopus")
                args.add("-b:a"); args.add("192k")
            }
            "mp4", "mov" -> {
                args.add("-c:a"); args.add("aac")
                args.add("-b:a"); args.add("192k")
                args.add("-movflags"); args.add("+faststart")
            }
            else -> {
                args.add("-c:a"); args.add("aac")
                args.add("-b:a"); args.add("192k")
            }
        }
    }

    private fun computeExpectedDuration(
        action: LocalFfmpegAction,
        inputDurationSec: Double?,
        startSec: Double?,
        endSec: Double?
    ): Double? {
        if (action != LocalFfmpegAction.TRIM_CLIP) return inputDurationSec
        if (endSec != null) {
            return (endSec - (startSec ?: 0.0)).coerceAtLeast(0.1)
        }
        if (inputDurationSec != null && startSec != null) {
            return (inputDurationSec - startSec).coerceAtLeast(0.1)
        }
        return inputDurationSec
    }

    private fun parseDurationSeconds(line: String): Double? {
        val m = DURATION_REGEX.find(line) ?: return null
        return hmsToSeconds(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    private fun parseProgressTimeSeconds(line: String): Double? {
        val m = TIME_REGEX.find(line) ?: return null
        return hmsToSeconds(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    private fun hmsToSeconds(h: String, m: String, s: String): Double? {
        val hours = h.toDoubleOrNull() ?: return null
        val mins = m.toDoubleOrNull() ?: return null
        val secs = s.toDoubleOrNull() ?: return null
        return hours * 3600.0 + mins * 60.0 + secs
    }

    private fun formatFfmpegSeconds(seconds: Double): String =
        String.format(Locale.US, "%.3f", seconds.coerceAtLeast(0.0))

    private fun isProcessAlive(process: Process): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.isAlive
        } else {
            try {
                process.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }
    }

    private fun destroyProcessSafely(process: Process) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.destroyForcibly()
            } else {
                process.destroy()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val FFMPEG_BIN_NAME = "libffmpeg.so"
        private const val YOUTUBEDL_BASE_DIR = "youtubedl-android"
        private const val PACKAGES_DIR = "packages"
        private const val FFMPEG_PACKAGE_DIR = "ffmpeg"
        private const val MAX_ERROR_LINES = 24

        private val MP4_FAMILY_EXTS = setOf("mp4", "m4a", "mov")
        private val DURATION_REGEX = Regex("""Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)""")
        private val TIME_REGEX = Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")
        private val SPEED_REGEX = Regex("""speed=\s*([0-9.]+x)""")

        /**
         * Parses `"SS"`, `"MM:SS"`, or `"HH:MM:SS"` (with optional fractional seconds) into seconds.
         * Returns `null` if [raw] is blank; throws [IllegalArgumentException] on invalid syntax.
         */
        fun parseTimestampToSeconds(raw: String): Double? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split(":")
            if (parts.size !in 1..3) {
                throw IllegalArgumentException("Neplatný čas '$trimmed'. Použijte formát MM:SS nebo HH:MM:SS.")
            }
            val nums = parts.map { part ->
                part.toDoubleOrNull()?.takeIf { !it.isNaN() && it >= 0.0 }
                    ?: throw IllegalArgumentException("Neplatný čas '$trimmed'. Použijte formát MM:SS nebo HH:MM:SS.")
            }
            return when (nums.size) {
                1 -> nums[0]
                2 -> {
                    if (nums[1] >= 60.0) {
                        throw IllegalArgumentException("Sekundy v '$trimmed' musí být menší než 60.")
                    }
                    nums[0] * 60.0 + nums[1]
                }
                else -> {
                    if (nums[1] >= 60.0 || nums[2] >= 60.0) {
                        throw IllegalArgumentException("Minuty a sekundy v '$trimmed' musí být menší než 60.")
                    }
                    nums[0] * 3600.0 + nums[1] * 60.0 + nums[2]
                }
            }
        }
    }
}
