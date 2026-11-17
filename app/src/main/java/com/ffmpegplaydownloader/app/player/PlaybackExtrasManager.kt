package com.ffmpegplaydownloader.app.player

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Presets for the playback sleep timer.
 * [minutes] > 0 represents a fixed countdown in minutes; 0 disables the timer;
 * -1 means stop playback after the current track finishes.
 */
enum class SleepTimerPreset(val label: String, val minutes: Int) {
    OFF("Vypnuto", 0),
    MIN_15("15 min", 15),
    MIN_30("30 min", 30),
    MIN_45("45 min", 45),
    MIN_60("60 min", 60),
    END_OF_TRACK("Po skladbě", -1)
}

/**
 * Queue and single-track repeat modes.
 */
enum class RepeatModeOption(val label: String) {
    OFF("Vypnuto"),
    ONE("Opakovat 1"),
    ALL("Opakovat vše")
}

/**
 * Supported playback speed presets.
 */
val PLAYBACK_SPEED_PRESETS: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/**
 * Formats a remaining sleep-timer duration in seconds as `MM:SS` or `H:MM:SS`.
 */
fun formatRemainingTime(remainingSec: Long): String {
    val safeSec = remainingSec.coerceAtLeast(0L)
    val hours = safeSec / 3600L
    val minutes = (safeSec % 3600L) / 60L
    val seconds = safeSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Shared constants and formatting helpers for playback speed, repeat, and sleep timer extras.
 */
object PlaybackExtrasManager {
    val speedPresets: List<Float> = PLAYBACK_SPEED_PRESETS

    fun formatRemainingTime(remainingSec: Long): String =
        com.ffmpegplaydownloader.app.player.formatRemainingTime(remainingSec)

    fun formatSpeedLabel(speed: Float): String {
        val hundredths = (speed * 100f).roundToInt()
        return if (hundredths % 10 == 0) {
            String.format(Locale.US, "%.1fx", hundredths / 100f)
        } else {
            String.format(Locale.US, "%.2fx", hundredths / 100f)
        }
    }
}
