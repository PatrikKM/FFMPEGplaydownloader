package com.ffmpegplaydownloader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.data.ShareCastHelper
import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.ui.theme.AppTheme

private enum class PendingStreamAction {
    CAST_VIDEO,
    CAST_AUDIO,
    COPY_STREAM_URL
}

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "wav", "opus", "ogg", "aac", "wma")

private fun isLocalAudioItem(item: MediaItem): Boolean {
    val ext = item.ext.ifBlank { item.localPath.substringAfterLast('.', "") }.lowercase()
    return ext in AUDIO_EXTENSIONS
}

@Composable
fun ShareAndCastDialog(
    item: MediaItem,
    resolvedStreamUrl: String?,
    resolvedAudioUrl: String?,
    isResolvingStream: Boolean,
    onDismiss: () -> Unit,
    onRequestResolveStream: () -> Unit,
    onShowStatus: (String) -> Unit
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var pendingAction by remember(item.id, item.url) { mutableStateOf<PendingStreamAction?>(null) }
    var wasResolving by remember(item.id, item.url) { mutableStateOf(false) }
    var inlineNotice by remember(item.id, item.url) { mutableStateOf<String?>(null) }
    var inlineNoticeIsError by remember(item.id, item.url) { mutableStateOf(false) }

    fun reportNotice(message: String, isError: Boolean = false) {
        inlineNotice = message
        inlineNoticeIsError = isError
        onShowStatus(message)
    }

    LaunchedEffect(resolvedStreamUrl, resolvedAudioUrl, isResolvingStream) {
        if (isResolvingStream) {
            wasResolving = true
            return@LaunchedEffect
        }
        val activeAction = pendingAction ?: run {
            wasResolving = false
            return@LaunchedEffect
        }

        val videoCandidate = resolvedStreamUrl?.takeIf { it.isNotBlank() }
        val audioCandidate = resolvedAudioUrl?.takeIf { it.isNotBlank() } ?: videoCandidate

        when (activeAction) {
            PendingStreamAction.CAST_VIDEO -> {
                if (videoCandidate != null) {
                    pendingAction = null
                    wasResolving = false
                    val launched = ShareCastHelper.openDirectStreamInCastOrExternalPlayer(
                        context = context,
                        streamUrl = videoCandidate,
                        title = item.title,
                        isAudio = false
                    )
                    if (!launched) {
                        reportNotice("Nenalezen Cast ani externí video přehrávač.", isError = true)
                    }
                } else if (wasResolving) {
                    pendingAction = null
                    wasResolving = false
                    reportNotice("Nepodařilo se získat přímou URL video streamu.", isError = true)
                }
            }

            PendingStreamAction.CAST_AUDIO -> {
                if (audioCandidate != null) {
                    pendingAction = null
                    wasResolving = false
                    val launched = ShareCastHelper.openDirectStreamInCastOrExternalPlayer(
                        context = context,
                        streamUrl = audioCandidate,
                        title = item.title,
                        isAudio = true
                    )
                    if (!launched) {
                        reportNotice("Nenalezen externí audio ani Cast přehrávač.", isError = true)
                    }
                } else if (wasResolving) {
                    pendingAction = null
                    wasResolving = false
                    reportNotice("Nepodařilo se získat přímou URL audio streamu.", isError = true)
                }
            }

            PendingStreamAction.COPY_STREAM_URL -> {
                val copyTarget = videoCandidate ?: audioCandidate
                if (copyTarget != null) {
                    pendingAction = null
                    wasResolving = false
                    clipboardManager.setText(AnnotatedString(copyTarget))
                    reportNotice("Přímá URL streamu zkopírována do schránky.")
                } else if (wasResolving) {
                    pendingAction = null
                    wasResolving = false
                    reportNotice("Nepodařilo se získat přímou URL streamu.", isError = true)
                }
            }
        }
    }

    val hasReadyVideoStream = item.isLocal || !resolvedStreamUrl.isNullOrBlank()
    val hasReadyAudioStream = item.isLocal || !resolvedAudioUrl.isNullOrBlank() || !resolvedStreamUrl.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = null,
                    tint = colors.primaryBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Sdílet, Cast & Zrcadlení",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Item preview card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgElevated)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlatformBadge(
                            platform = if (item.isLocal) MediaPlatform.LOCAL else item.platform,
                            extractor = item.extractor,
                            contentKind = item.contentKind
                        )
                        if (item.durationString.isNotBlank()) {
                            Text(
                                text = item.durationString,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Text(
                        text = item.title.ifBlank { "Bez názvu" },
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleText = when {
                        item.channel.isNotBlank() -> item.channel
                        item.isLocal && item.ext.isNotBlank() -> "Lokální soubor (${item.ext.uppercase()})"
                        else -> ""
                    }
                    if (subtitleText.isNotBlank()) {
                        Text(
                            text = subtitleText,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (inlineNotice != null) {
                    val noticeColor = if (inlineNoticeIsError) colors.warningAmber else colors.successGreen
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(noticeColor.copy(alpha = 0.14f))
                            .border(1.dp, noticeColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = noticeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = inlineNotice!!,
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Section 1: Chromecast, TV & Mirroring
                SectionHeader("📺 Chromecast, TV & Zrcadlení")

                ShareActionTile(
                    icon = Icons.Default.Tv,
                    accentColor = colors.primaryBlue,
                    title = "Zrcadlení obrazovky (Cast / Wireless Display)",
                    subtitle = "Otevře systémový výběr Chromecast, Smart TV nebo bezdrátové obrazovky",
                    onClick = {
                        val opened = ShareCastHelper.openSystemCastAndMirroring(context)
                        if (!opened) {
                            reportNotice("Systémové nastavení Cast / Zrcadlení není na tomto zařízení dostupné.", isError = true)
                        }
                    }
                )

                val resolvingVideo = isResolvingStream && pendingAction == PendingStreamAction.CAST_VIDEO
                ShareActionTile(
                    icon = Icons.Default.CastConnected,
                    accentColor = colors.successGreen,
                    title = "Odeslat video stream do Cast / externího přehrávače",
                    subtitle = when {
                        resolvingVideo -> "Získávám přímou adresu video streamu…"
                        item.isLocal -> "Přehrát lokální soubor ve VLC, Web Video Caster, BubbleUPnP, TV…"
                        else -> "VLC, Web Video Caster, BubbleUPnP, Kodi, Smart TV…"
                    },
                    isLoading = resolvingVideo,
                    badgeText = if (!item.isLocal && hasReadyVideoStream && !resolvingVideo) "Připraveno" else null,
                    onClick = {
                        if (item.isLocal && item.localPath.isNotBlank()) {
                            val ok = ShareCastHelper.openDirectStreamInCastOrExternalPlayer(
                                context = context,
                                streamUrl = item.localPath,
                                title = item.title,
                                isAudio = false
                            )
                            if (!ok) {
                                reportNotice("Soubor nelze otevřít v externím video přehrávači.", isError = true)
                            }
                        } else if (!resolvedStreamUrl.isNullOrBlank()) {
                            val ok = ShareCastHelper.openDirectStreamInCastOrExternalPlayer(
                                context = context,
                                streamUrl = resolvedStreamUrl,
                                title = item.title,
                                isAudio = false
                            )
                            if (!ok) {
                                reportNotice("Nenalezen Cast ani externí video přehrávač.", isError = true)
                            }
                        } else {
                            inlineNotice = null
                            pendingAction = PendingStreamAction.CAST_VIDEO
                            if (!isResolvingStream) {
                                onRequestResolveStream()
                            }
                        }
                    }
                )

                val resolvingAudio = isResolvingStream && pendingAction == PendingStreamAction.CAST_AUDIO
                ShareActionTile(
                    icon = Icons.Default.MusicNote,
                    accentColor = colors.youTubeMusicPink,
                    title = "Odeslat audio stream do reproduktoru / přehrávače",
                    subtitle = when {
                        resolvingAudio -> "Získávám přímou adresu audio streamu…"
                        else -> "Odeslat zvuk do Cast reproduktoru, Hi-Fi nebo audio přehrávače"
                    },
                    isLoading = resolvingAudio,
                    badgeText = if (!item.isLocal && hasReadyAudioStream && !resolvingAudio) "Připraveno" else null,
                    onClick = {
                        if (item.isLocal && item.localPath.isNotBlank()) {
                            val ok = ShareCastHelper.openDirectStreamInCastOrExternalPlayer(
                                context = context,
                                streamUrl = item.localPath,
                                title = item.title,
                                isAudio = true
                            )
                            if (!ok) {
                                reportNotice("Soubor nelze otevřít v externím audio přehrávači.", isError = true)
                            }
                        } else {
                            val directAudio = resolvedAudioUrl?.takeIf { it.isNotBlank() }
                                ?: resolvedStreamUrl?.takeIf { it.isNotBlank() }
                            if (directAudio != null) {
                                val ok = ShareCastHelper.openDirectStreamInCastOrExternalPlayer(
                                    context = context,
                                    streamUrl = directAudio,
                                    title = item.title,
                                    isAudio = true
                                )
                                if (!ok) {
                                    reportNotice("Nenalezen externí audio ani Cast přehrávač.", isError = true)
                                }
                            } else {
                                inlineNotice = null
                                pendingAction = PendingStreamAction.CAST_AUDIO
                                if (!isResolvingStream) {
                                    onRequestResolveStream()
                                }
                            }
                        }
                    }
                )

                HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 2.dp))

                // Section 2: Share & Broadcast
                SectionHeader("📡 Sdílet & Broadcast do aplikací")

                ShareActionTile(
                    icon = Icons.Default.Share,
                    accentColor = colors.primaryBlue,
                    title = "Sdílet do aplikací (Broadcast / Zprávy / Bluetooth / Nearby…)",
                    subtitle = if (item.isLocal) {
                        "Odešle stažený mediální soubor do vybrané aplikace"
                    } else {
                        "Odešle název a odkaz přes systémovou nabídku sdílení"
                    },
                    onClick = {
                        val shared = if (item.isLocal && item.localPath.isNotBlank()) {
                            ShareCastHelper.shareLocalFile(
                                context = context,
                                filePath = item.localPath,
                                isAudio = isLocalAudioItem(item)
                            )
                        } else {
                            ShareCastHelper.shareLinkBroadcast(
                                context = context,
                                title = item.title,
                                url = item.url
                            )
                        }
                        if (!shared) {
                            reportNotice("Sdílení do aplikací se nepodařilo spustit.", isError = true)
                        }
                    }
                )

                if (!item.isLocal && item.url.isNotBlank()) {
                    ShareActionTile(
                        icon = Icons.Default.Link,
                        accentColor = colors.warningAmber,
                        title = "Kopírovat odkaz (${item.platform.shortLabel})",
                        subtitle = item.url,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.url))
                            reportNotice("Odkaz (${item.platform.shortLabel}) zkopírován do schránky.")
                        }
                    )
                }

                if (!item.isLocal) {
                    val resolvingCopy = isResolvingStream && pendingAction == PendingStreamAction.COPY_STREAM_URL
                    ShareActionTile(
                        icon = Icons.Default.ContentCopy,
                        accentColor = colors.successGreen,
                        title = "Kopírovat přímou URL streamu",
                        subtitle = if (resolvingCopy) {
                            "Získávám přímou URL streamu pro VLC / IPTV / Kodi…"
                        } else {
                            "Pro VLC, IPTV, Kodi, Web Video Caster nebo vlastní přehrávač"
                        },
                        isLoading = resolvingCopy,
                        badgeText = if (hasReadyVideoStream && !resolvingCopy) "Připraveno" else null,
                        onClick = {
                            val directUrl = resolvedStreamUrl?.takeIf { it.isNotBlank() }
                                ?: resolvedAudioUrl?.takeIf { it.isNotBlank() }
                            if (directUrl != null) {
                                clipboardManager.setText(AnnotatedString(directUrl))
                                reportNotice("Přímá URL streamu zkopírována do schránky.")
                            } else {
                                inlineNotice = null
                                pendingAction = PendingStreamAction.COPY_STREAM_URL
                                if (!isResolvingStream) {
                                    onRequestResolveStream()
                                }
                            }
                        }
                    )
                } else if (item.localPath.isNotBlank()) {
                    ShareActionTile(
                        icon = Icons.Default.ContentCopy,
                        accentColor = colors.warningAmber,
                        title = "Kopírovat cestu k souboru",
                        subtitle = item.localPath,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(item.localPath))
                            reportNotice("Cesta k souboru zkopírována do schránky.")
                        }
                    )
                }

                if (!item.isLocal && item.url.isNotBlank()) {
                    ShareActionTile(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        accentColor = colors.textSecondary,
                        title = "Otevřít v prohlížeči / původní aplikaci",
                        subtitle = "Spustí originální zdroj (${item.platform.displayName})",
                        onClick = {
                            val opened = ShareCastHelper.openInBrowserOrApp(context, item.url)
                            if (!opened) {
                                reportNotice("Nepodařilo se otevřít odkaz v prohlížeči ani aplikaci.", isError = true)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Zavřít", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = AppTheme.colors.textPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun ShareActionTile(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgElevated)
            .border(
                width = 1.dp,
                color = if (isLoading) accentColor.copy(alpha = 0.7f) else colors.border,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.16f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )
            }
            badgeText != null -> {
                Text(
                    text = badgeText,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            else -> {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
