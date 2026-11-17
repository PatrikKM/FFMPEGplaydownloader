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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.data.LocalFfmpegAction
import com.ffmpegplaydownloader.app.data.LocalFfmpegProcessor
import com.ffmpegplaydownloader.app.model.LocalMediaFile
import com.ffmpegplaydownloader.app.ui.theme.AppColors
import java.util.Locale

@Composable
fun LocalFfmpegDialog(
    file: LocalMediaFile,
    isProcessing: Boolean = false,
    progress: Float = 0f,
    statusMessage: String = "",
    onDismiss: () -> Unit,
    onStartProcessing: (
        action: LocalFfmpegAction,
        trimStart: String,
        trimEnd: String
    ) -> Unit
) {
    var selectedAction by remember(file.path) {
        mutableStateOf(
            if (file.isAudio) LocalFfmpegAction.NORMALIZE_LOUDNESS
            else LocalFfmpegAction.EXTRACT_MP3_320
        )
    }
    var trimStart by remember(file.path) { mutableStateOf("") }
    var trimEnd by remember(file.path) { mutableStateOf("") }

    val trimValidationError = remember(selectedAction, trimStart, trimEnd) {
        if (selectedAction != LocalFfmpegAction.TRIM_CLIP) {
            null
        } else {
            val startResult = runCatching { LocalFfmpegProcessor.parseTimestampToSeconds(trimStart) }
            val endResult = runCatching { LocalFfmpegProcessor.parseTimestampToSeconds(trimEnd) }
            when {
                startResult.isFailure -> startResult.exceptionOrNull()?.message
                endResult.isFailure -> endResult.exceptionOrNull()?.message
                startResult.getOrNull() == null && endResult.getOrNull() == null ->
                    "Zadejte alespoň čas Od nebo Do (např. 0:15)."
                startResult.getOrNull() != null &&
                    endResult.getOrNull() != null &&
                    endResult.getOrNull()!! <= startResult.getOrNull()!! ->
                    "Čas Do musí být větší než čas Od."
                else -> null
            }
        }
    }

    val canLaunch = !isProcessing &&
        trimValidationError == null &&
        !(file.isAudio && selectedAction == LocalFfmpegAction.COMPRESS_720P)

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        containerColor = AppColors.BgSurface,
        title = {
            Text(
                "⚙️ Lokální FFmpeg konverze",
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.BgElevated)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${if (file.isAudio) "🎵" else "🎬"} ${file.name}",
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${file.ext} • ${formatLocalFileBytes(file.sizeBytes)}",
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    "Vyberte operaci:",
                    color = AppColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalFfmpegAction.entries.forEach { action ->
                        val enabled = !isProcessing &&
                            !(file.isAudio && action == LocalFfmpegAction.COMPRESS_720P)
                        val selected = selectedAction == action
                        val borderColor = when {
                            selected -> AppColors.PrimaryBlue
                            else -> AppColors.Border
                        }
                        val bgColor = when {
                            selected -> AppColors.PrimaryBlue.copy(alpha = 0.16f)
                            else -> AppColors.BgElevated
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (enabled || selected) 1f else 0.45f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled) { selectedAction = action }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.dp,
                                        color = if (selected) AppColors.PrimaryBlue else AppColors.TextSecondary,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AppColors.PrimaryBlue)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = action.label,
                                    color = AppColors.TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (file.isAudio && action == LocalFfmpegAction.COMPRESS_720P) {
                                    Text(
                                        text = "Dostupné pouze pro video soubory",
                                        color = AppColors.TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedAction == LocalFfmpegAction.TRIM_CLIP) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Časový výřez (formát MM:SS nebo HH:MM:SS):",
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = trimStart,
                                onValueChange = { trimStart = it },
                                enabled = !isProcessing,
                                label = { Text("Od (MM:SS)", fontSize = 11.sp) },
                                placeholder = { Text("0:15", fontSize = 11.sp, color = AppColors.TextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.PrimaryBlue,
                                    unfocusedBorderColor = AppColors.Border,
                                    focusedTextColor = AppColors.TextPrimary,
                                    unfocusedTextColor = AppColors.TextPrimary,
                                    focusedLabelColor = AppColors.PrimaryBlue,
                                    unfocusedLabelColor = AppColors.TextSecondary,
                                    focusedContainerColor = AppColors.BgMain,
                                    unfocusedContainerColor = AppColors.BgMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = trimEnd,
                                onValueChange = { trimEnd = it },
                                enabled = !isProcessing,
                                label = { Text("Do (MM:SS)", fontSize = 11.sp) },
                                placeholder = { Text("1:45", fontSize = 11.sp, color = AppColors.TextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.PrimaryBlue,
                                    unfocusedBorderColor = AppColors.Border,
                                    focusedTextColor = AppColors.TextPrimary,
                                    unfocusedTextColor = AppColors.TextPrimary,
                                    focusedLabelColor = AppColors.PrimaryBlue,
                                    unfocusedLabelColor = AppColors.TextSecondary,
                                    focusedContainerColor = AppColors.BgMain,
                                    unfocusedContainerColor = AppColors.BgMain
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (trimValidationError != null && (trimStart.isNotBlank() || trimEnd.isNotBlank())) {
                            Text(
                                text = trimValidationError,
                                color = AppColors.WarningAmber,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (isProcessing || statusMessage.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.BgMain)
                            .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            color = AppColors.SuccessGreen,
                            trackColor = AppColors.BgElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )
                        if (statusMessage.isNotBlank()) {
                            Text(
                                text = statusMessage,
                                color = AppColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStartProcessing(
                        selectedAction,
                        trimStart.trim(),
                        trimEnd.trim()
                    )
                },
                enabled = canLaunch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.PrimaryBlue,
                    disabledContainerColor = AppColors.BgElevated
                )
            ) {
                Text(
                    if (isProcessing) "Zpracovávám..." else "🚀 Spustit FFmpeg",
                    color = if (canLaunch) Color.White else AppColors.TextSecondary
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text(
                    "Zrušit",
                    color = if (!isProcessing) AppColors.TextSecondary else AppColors.Border
                )
            }
        }
    )
}

private fun formatLocalFileBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
