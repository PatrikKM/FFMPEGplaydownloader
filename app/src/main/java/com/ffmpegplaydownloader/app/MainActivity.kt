package com.ffmpegplaydownloader.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ffmpegplaydownloader.app.player.PipHelper
import com.ffmpegplaydownloader.app.ui.components.MainScreen
import com.ffmpegplaydownloader.app.ui.theme.FfmpegPlayDownloaderTheme
import com.ffmpegplaydownloader.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    var onPipModeChanged: ((Boolean) -> Unit)? = null
    var shouldAutoEnterPip: () -> Boolean = { false }
    var isVideoPipMode: () -> Boolean = { false }

    var isInPipMode: Boolean = false
        private set

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        handleIncomingShareIntent(intent)

        onPipModeChanged = { inPip -> viewModel.setPipMode(inPip) }
        shouldAutoEnterPip = { viewModel.canAutoEnterPip() }
        isVideoPipMode = { viewModel.isVideoPipMode() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            isInPipMode = isInPictureInPictureMode
            viewModel.setPipMode(isInPictureInPictureMode)
        }

        setContent {
            FfmpegPlayDownloaderTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldAutoEnterPip()) {
            PipHelper.enterPip(this, isVideoMode = isVideoPipMode())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        onPipModeChanged?.invoke(isInPictureInPictureMode)
    }

    fun enterPipMode(isVideoMode: Boolean = isVideoPipMode()): Boolean {
        return PipHelper.enterPip(this, isVideoMode = isVideoMode)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrBlank()) {
                val urlMatch = Regex("""https?://\S+""").find(sharedText)?.value ?: sharedText
                viewModel.search(queryOverride = urlMatch)
            }
        }
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.READ_MEDIA_VIDEO
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.READ_MEDIA_AUDIO
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
