package com.ffmpegplaydownloader.app.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

/**
 * Helper for Android Picture-in-Picture (API 26+).
 */
object PipHelper {

    fun isPipSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } catch (_: Exception) {
            false
        }
    }

    fun enterPip(activity: Activity, isVideoMode: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!isPipSupported(activity)) return false
        if (activity.isFinishing || activity.isDestroyed) return false

        return try {
            val aspectRatio = if (isVideoMode) Rational(16, 9) else Rational(1, 1)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (_: Exception) {
            false
        }
    }
}
