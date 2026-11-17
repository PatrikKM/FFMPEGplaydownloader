package com.ffmpegplaydownloader.app.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ffmpegplaydownloader.app.MainActivity

/**
 * Shared ExoPlayer holder within the app process so PlaybackService manages
 * background/screen-off foreground execution and notifications while the UI
 * can attach Video Surface, MergingMediaSource, and AudioEffects directly.
 */
object PlayerEngine {
    @Volatile
    private var exoPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing
        synchronized(this) {
            val recheck = exoPlayer
            if (recheck != null) return recheck

            val appContext = context.applicationContext
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            return ExoPlayer.Builder(appContext)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
                .also { exoPlayer = it }
        }
    }

    fun releasePlayer(expectedPlayer: ExoPlayer? = null) {
        synchronized(this) {
            if (expectedPlayer != null && exoPlayer !== expectedPlayer) return
            val player = exoPlayer
            exoPlayer = null
            if (player != null) {
                try {
                    player.playWhenReady = false
                    player.stop()
                } catch (_: Exception) {
                }
                try {
                    player.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun shutdown(context: Context) {
        val appContext = context.applicationContext
        try {
            appContext.stopService(Intent(appContext, PlaybackService::class.java))
        } catch (_: Exception) {
        }
        releasePlayer()
    }
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerEngine.getOrCreatePlayer(this)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (
            player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            try {
                player?.stop()
            } catch (_: Exception) {
            }
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        val sessionPlayer = mediaSession?.player as? ExoPlayer
        mediaSession?.run {
            try {
                player.stop()
            } catch (_: Exception) {
            }
            try {
                release()
            } catch (_: Exception) {
            }
            mediaSession = null
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        if (sessionPlayer != null) {
            PlayerEngine.releasePlayer(sessionPlayer)
        }
        super.onDestroy()
    }
}
