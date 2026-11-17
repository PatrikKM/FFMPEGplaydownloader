package com.ffmpegplaydownloader.app.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object ShareCastHelper {
    private val SYSTEM_CAST_ACTIONS = listOf(
        Settings.ACTION_CAST_SETTINGS,
        "android.settings.WIFI_DISPLAY_SETTINGS",
        "com.samsung.wfd.LAUNCH_WFD_PICKER_DLG",
        Settings.ACTION_DISPLAY_SETTINGS,
        Settings.ACTION_WIRELESS_SETTINGS,
        Settings.ACTION_SETTINGS
    )

    fun shareLinkBroadcast(context: Context, title: String, url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val cleanTitle = title.trim()
            val cleanUrl = url.trim()
            val shareText = if (cleanTitle.isNotEmpty()) "$cleanTitle\n$cleanUrl" else cleanUrl
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (cleanTitle.isNotEmpty()) {
                    putExtra(Intent.EXTRA_SUBJECT, cleanTitle)
                    putExtra(Intent.EXTRA_TITLE, cleanTitle)
                }
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            val chooser = Intent.createChooser(sendIntent, "Sdílet přes...")
            launchChooserSafely(context, chooser)
        } catch (_: Exception) {
            false
        }
    }

    fun shareLocalFile(context: Context, filePath: String, isAudio: Boolean): Boolean {
        if (filePath.isBlank()) return false
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) return false
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mimeType = if (isAudio) "audio/*" else "video/*"
            val clip = ClipData.newUri(context.contentResolver, file.name, uri)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Sdílet soubor").apply {
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            launchChooserSafely(context, chooser)
        } catch (_: Exception) {
            false
        }
    }

    fun openDirectStreamInCastOrExternalPlayer(
        context: Context,
        streamUrl: String,
        title: String,
        isAudio: Boolean
    ): Boolean {
        if (streamUrl.isBlank()) return false
        return try {
            val trimmed = streamUrl.trim()
            val isFileReference = trimmed.startsWith("file://", ignoreCase = true) || !trimmed.contains("://")
            val uri: Uri
            val grantsReadPermission: Boolean

            if (isFileReference) {
                val localFile = resolveLocalFileIfAny(trimmed) ?: return false
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
                grantsReadPermission = true
            } else {
                uri = Uri.parse(trimmed)
                grantsReadPermission = trimmed.startsWith("content://", ignoreCase = true)
            }

            val mimeType = if (isAudio) "audio/*" else "video/*"
            val clip = if (grantsReadPermission) {
                ClipData.newUri(context.contentResolver, title.ifBlank { "media" }, uri)
            } else {
                null
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                if (title.isNotBlank()) {
                    putExtra("title", title)
                    putExtra(Intent.EXTRA_TITLE, title)
                }
                if (grantsReadPermission) {
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            val chooser = Intent.createChooser(viewIntent, "Odeslat / Chromecast / Přehrát v...").apply {
                if (grantsReadPermission) {
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            launchChooserSafely(context, chooser)
        } catch (_: Exception) {
            false
        }
    }

    fun openSystemCastAndMirroring(context: Context): Boolean {
        for (action in SYSTEM_CAST_ACTIONS) {
            try {
                val intent = Intent(action)
                if (launchChooserSafely(context, intent)) {
                    return true
                }
            } catch (_: ActivityNotFoundException) {
                // Try next fallback settings screen
            } catch (_: SecurityException) {
                // Some OEM-specific actions reject non-system callers
            } catch (_: Exception) {
                // Continue to fallback
            }
        }
        return false
    }

    fun openInBrowserOrApp(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
            launchChooserSafely(context, intent)
        } catch (_: Exception) {
            false
        }
    }

    fun launchChooserSafely(context: Context, intent: Intent): Boolean {
        return try {
            if (findActivity(context) == null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveLocalFileIfAny(pathOrUrl: String): File? {
        val candidate = if (pathOrUrl.startsWith("file://", ignoreCase = true)) {
            val parsedPath = Uri.parse(pathOrUrl).path ?: return null
            File(parsedPath)
        } else {
            File(pathOrUrl)
        }
        return candidate.takeIf { it.exists() && it.isFile }
    }

    private tailrec fun findActivity(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}
