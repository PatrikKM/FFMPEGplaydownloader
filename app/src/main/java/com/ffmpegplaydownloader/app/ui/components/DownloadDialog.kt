package com.ffmpegplaydownloader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.model.DownloadFormat
import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.ui.theme.AppColors

@Composable
fun DownloadDialog(
    item: MediaItem,
    onDismiss: () -> Unit,
    onStartDownload: (
        format: DownloadFormat,
        trimStart: String,
        trimEnd: String,
        embedSubs: Boolean,
        loudnorm: Boolean
    ) -> Unit
) {
    val defaultFormat = if (item.platform == MediaPlatform.YOUTUBE_MUSIC) {
        DownloadFormat.MP3_320
    } else {
        DownloadFormat.MP4_1080
    }
    var selectedFormat by remember(item.url) { mutableStateOf(defaultFormat) }
    var formatMenuOpen by remember { mutableStateOf(false) }
    var trimStart by remember { mutableStateOf("") }
    var trimEnd by remember { mutableStateOf("") }
    var embedSubs by remember { mutableStateOf(true) }
    var loudnorm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.BgSurface,
        title = {
            Text(
                "⬇ Stáhnout a konvertovat přes FFmpeg",
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlatformBadge(
                    platform = item.platform,
                    extractor = item.extractor,
                    contentKind = item.contentKind
                )
                Text(
                    item.title,
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text("Výstupní formát a kvalita:", color = AppColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box {
                    OutlinedButton(
                        onClick = { formatMenuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(selectedFormat.label, color = AppColors.TextPrimary, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = formatMenuOpen,
                        onDismissRequest = { formatMenuOpen = false },
                        modifier = Modifier.background(AppColors.BgElevated)
                    ) {
                        DownloadFormat.entries.forEach { fmt ->
                            DropdownMenuItem(
                                text = { Text(fmt.label, color = AppColors.TextPrimary, fontSize = 12.sp) },
                                onClick = {
                                    selectedFormat = fmt
                                    formatMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = trimStart,
                        onValueChange = { trimStart = it },
                        label = { Text("Výřez OD", fontSize = 11.sp) },
                        placeholder = { Text("0:30", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = trimEnd,
                        onValueChange = { trimEnd = it },
                        label = { Text("Výřez DO", fontSize = 11.sp) },
                        placeholder = { Text("2:45", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!selectedFormat.isAudio) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = embedSubs, onCheckedChange = { embedSubs = it })
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "💬 Vložit české titulky (jsou-li dostupné)",
                            color = AppColors.TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = loudnorm, onCheckedChange = { loudnorm = it })
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "🔊 FFmpeg normalizace hlasitosti (Loudnorm)",
                        color = AppColors.TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStartDownload(selectedFormat, trimStart, trimEnd, embedSubs, loudnorm) },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryBlue)
            ) {
                Text("🚀 Spustit FFmpeg", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zrušit", color = AppColors.TextSecondary)
            }
        }
    )
}
