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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.data.LocalFfmpegAction
import com.ffmpegplaydownloader.app.data.LocalFfmpegProcessor
import com.ffmpegplaydownloader.app.model.LocalMediaFile
import com.ffmpegplaydownloader.app.ui.theme.AppTheme
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
    val colors = AppTheme.colors
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

    val trimFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primaryBlue,
        unfocusedBorderColor = colors.border,
        disabledBorderColor = colors.border.copy(alpha = 0.5f),
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        disabledTextColor = colors.textSecondary,
        focusedLabelColor = colors.primaryBlue,
        unfocusedLabelColor = colors.textSecondary,
        disabledLabelColor = colors.textSecondary,
        focusedContainerColor = colors.bgMain,
        unfocusedContainerColor = colors.bgMain,
        disabledContainerColor = colors.bgMain,
        cursorColor = colors.primaryBlue
    )

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        containerColor = colors.bgSurface,
        title = {
            Text(
                "⚙️ Lokální FFmpeg konverze",
                color = colors.textPrimary,
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
                        .background(colors.bgElevated)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${if (file.isAudio) "🎵" else "🎬"} ${file.name}",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${file.ext} • ${formatLocalFileBytes(file.sizeBytes)}",
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }

                Text(
                    "Vyberte operaci:",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalFfmpegAction.entries.forEach { action ->
                        val enabled = !isProcessing &&
                            !(file.isAudio && action == LocalFfmpegAction.COMPRESS_720P)
                        val selected = selectedAction == action
                        val borderColor = when {
                            selected -> colors.primaryBlue
                            else -> colors.border
                        }
                        val bgColor = when {
                            selected -> colors.primaryBlue.copy(
                                alpha = if (colors.isDark) 0.16f else 0.10f
                            )
                            else -> colors.bgElevated
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
                                        color = if (selected) colors.primaryBlue else colors.textSecondary,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(colors.primaryBlue)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = action.label,
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (file.isAudio && action == LocalFfmpegAction.COMPRESS_720P) {
                                    Text(
                                        text = "Dostupné pouze pro video soubory",
                                        color = colors.textSecondary,
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
                            color = colors.textSecondary,
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
                                placeholder = { Text("0:15", fontSize = 11.sp, color = colors.textSecondary) },
                                singleLine = true,
                                colors = trimFieldColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = trimEnd,
                                onValueChange = { trimEnd = it },
                                enabled = !isProcessing,
                                label = { Text("Do (MM:SS)", fontSize = 11.sp) },
                                placeholder = { Text("1:45", fontSize = 11.sp, color = colors.textSecondary) },
                                singleLine = true,
                                colors = trimFieldColors,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (trimValidationError != null && (trimStart.isNotBlank() || trimEnd.isNotBlank())) {
                            Text(
                                text = trimValidationError,
                                color = colors.warningAmber,
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
                            .background(colors.bgMain)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            color = colors.successGreen,
                            trackColor = if (colors.isDark) colors.bgElevated else colors.border,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )
                        if (statusMessage.isNotBlank()) {
                            Text(
                                text = statusMessage,
                                color = colors.textSecondary,
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
                    containerColor = colors.primaryBlue,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = colors.bgElevated,
                    disabledContentColor = colors.textSecondary
                )
            ) {
                Text(if (isProcessing) "Zpracovávám..." else "🚀 Spustit FFmpeg")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text(
                    "Zrušit",
                    color = if (!isProcessing) colors.textSecondary else colors.border
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
