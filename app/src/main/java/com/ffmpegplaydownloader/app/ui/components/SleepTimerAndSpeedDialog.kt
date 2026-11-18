package com.ffmpegplaydownloader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.player.PlaybackExtrasManager
import com.ffmpegplaydownloader.app.player.RepeatModeOption
import com.ffmpegplaydownloader.app.player.SleepTimerPreset
import com.ffmpegplaydownloader.app.player.formatRemainingTime
import com.ffmpegplaydownloader.app.ui.theme.AppTheme
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerAndSpeedDialog(
    playbackSpeed: Float,
    repeatMode: RepeatModeOption,
    shuffleEnabled: Boolean,
    sleepTimerPreset: SleepTimerPreset,
    sleepTimerRemainingSec: Long,
    onSelectSpeed: (Float) -> Unit,
    onSelectRepeatMode: (RepeatModeOption) -> Unit,
    onToggleShuffle: (Boolean) -> Unit,
    onSelectSleepTimer: (SleepTimerPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppTheme.colors
    val isSleepTimerActive = sleepTimerPreset != SleepTimerPreset.OFF

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "⏱️ Rychlost, opakování & časovač",
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Rychlost ${PlaybackExtrasManager.formatSpeedLabel(playbackSpeed)} • ${repeatMode.label}" +
                        if (shuffleEnabled) " • Náhodně" else "",
                    color = colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Playback speed section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Rychlost přehrávání",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            PlaybackExtrasManager.formatSpeedLabel(playbackSpeed),
                            color = if (abs(playbackSpeed - 1.0f) > 0.01f) colors.primaryBlue else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlaybackExtrasManager.speedPresets.forEach { speed ->
                            val selected = abs(playbackSpeed - speed) < 0.01f
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectSpeed(speed) },
                                label = {
                                    Text(
                                        PlaybackExtrasManager.formatSpeedLabel(speed),
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.bgElevated,
                                    labelColor = colors.textSecondary,
                                    selectedContainerColor = colors.primaryBlue.copy(
                                        alpha = if (colors.isDark) 0.25f else 0.15f
                                    ),
                                    selectedLabelColor = colors.textPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = colors.border,
                                    selectedBorderColor = colors.primaryBlue
                                ),
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }

                // Repeat mode + Shuffle section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Opakování a pořadí",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RepeatModeOption.entries.forEach { option ->
                            val selected = repeatMode == option
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectRepeatMode(option) },
                                label = {
                                    Text(
                                        option.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.bgElevated,
                                    labelColor = colors.textSecondary,
                                    selectedContainerColor = colors.primaryBlue.copy(
                                        alpha = if (colors.isDark) 0.25f else 0.15f
                                    ),
                                    selectedLabelColor = colors.textPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = colors.border,
                                    selectedBorderColor = colors.primaryBlue
                                ),
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.bgElevated)
                            .border(
                                width = 1.dp,
                                color = if (shuffleEnabled) colors.primaryBlue else colors.border,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🔀 Náhodné přehrávání",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (shuffleEnabled) "Fronta se přehrává v náhodném pořadí" else "Přehrávání postupně podle fronty",
                                color = colors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = shuffleEnabled,
                            onCheckedChange = onToggleShuffle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = colors.primaryBlue,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.bgSurface,
                                uncheckedBorderColor = colors.border
                            )
                        )
                    }
                }

                // Sleep Timer section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Časovač vypnutí",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (isSleepTimerActive) {
                            val badgeText = when {
                                sleepTimerPreset == SleepTimerPreset.END_OF_TRACK -> "⏳ Po skladbě"
                                sleepTimerRemainingSec > 0L -> "⏳ ${formatRemainingTime(sleepTimerRemainingSec)}"
                                else -> "⏳ ${sleepTimerPreset.label}"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.warningAmber.copy(alpha = 0.18f))
                                    .border(1.dp, colors.warningAmber, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    badgeText,
                                    color = colors.warningAmber,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SleepTimerPreset.entries.forEach { preset ->
                            val selected = sleepTimerPreset == preset
                            val activeNonOff = selected && preset != SleepTimerPreset.OFF
                            val selectedBg = if (activeNonOff) {
                                colors.warningAmber.copy(alpha = if (colors.isDark) 0.22f else 0.16f)
                            } else {
                                colors.primaryBlue.copy(alpha = if (colors.isDark) 0.25f else 0.15f)
                            }
                            val selectedBorder = if (activeNonOff) {
                                colors.warningAmber
                            } else {
                                colors.primaryBlue
                            }

                            FilterChip(
                                selected = selected,
                                onClick = { onSelectSleepTimer(preset) },
                                label = {
                                    Text(
                                        preset.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colors.bgElevated,
                                    labelColor = colors.textSecondary,
                                    selectedContainerColor = selectedBg,
                                    selectedLabelColor = if (activeNonOff) colors.warningAmber else colors.textPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = colors.border,
                                    selectedBorderColor = selectedBorder
                                ),
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onSelectSpeed(1.0f)
                    onSelectRepeatMode(RepeatModeOption.OFF)
                    onToggleShuffle(false)
                    onSelectSleepTimer(SleepTimerPreset.OFF)
                }
            ) {
                Text("Reset", color = colors.textSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryBlue,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Hotovo")
            }
        }
    )
}
