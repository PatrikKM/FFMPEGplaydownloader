package com.ffmpegplaydownloader.app

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class FFmpegPlayDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            Log.e("FFmpegPlayDownloaderApp", "Chyba inicializace yt-dlp / FFmpeg", e)
        }
    }
}
