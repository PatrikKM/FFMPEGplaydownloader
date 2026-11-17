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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.player.PlaybackExtrasManager
import com.ffmpegplaydownloader.app.player.RepeatModeOption
import com.ffmpegplaydownloader.app.player.SleepTimerPreset
import com.ffmpegplaydownloader.app.player.formatRemainingTime
import com.ffmpegplaydownloader.app.ui.theme.AppColors
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
    val isSleepTimerActive = sleepTimerPreset != SleepTimerPreset.OFF

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.BgSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "⏱️ Rychlost, opakování & časovač",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Rychlost ${PlaybackExtrasManager.formatSpeedLabel(playbackSpeed)} • ${repeatMode.label}" +
                        if (shuffleEnabled) " • Náhodně" else "",
                    color = AppColors.TextSecondary,
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
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            PlaybackExtrasManager.formatSpeedLabel(playbackSpeed),
                            color = if (abs(playbackSpeed - 1.0f) > 0.01f) AppColors.PrimaryBlue else AppColors.TextSecondary,
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
                                    containerColor = AppColors.BgElevated,
                                    labelColor = AppColors.TextSecondary,
                                    selectedContainerColor = AppColors.PrimaryBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = AppColors.TextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = AppColors.Border,
                                    selectedBorderColor = AppColors.PrimaryBlue
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
                        color = AppColors.TextPrimary,
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
                                    containerColor = AppColors.BgElevated,
                                    labelColor = AppColors.TextSecondary,
                                    selectedContainerColor = AppColors.PrimaryBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = AppColors.TextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = AppColors.Border,
                                    selectedBorderColor = AppColors.PrimaryBlue
                                ),
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.BgElevated)
                            .border(
                                width = 1.dp,
                                color = if (shuffleEnabled) AppColors.PrimaryBlue else AppColors.Border,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🔀 Náhodné přehrávání",
                                color = AppColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (shuffleEnabled) "Fronta se přehrává v náhodném pořadí" else "Přehrávání postupně podle fronty",
                                color = AppColors.TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = shuffleEnabled,
                            onCheckedChange = onToggleShuffle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppColors.PrimaryBlue,
                                uncheckedThumbColor = AppColors.TextSecondary,
                                uncheckedTrackColor = AppColors.BgSurface
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
                            color = AppColors.TextPrimary,
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
                                    .background(AppColors.WarningAmber.copy(alpha = 0.18f))
                                    .border(1.dp, AppColors.WarningAmber, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    badgeText,
                                    color = AppColors.WarningAmber,
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
                                AppColors.WarningAmber.copy(alpha = 0.22f)
                            } else {
                                AppColors.PrimaryBlue.copy(alpha = 0.25f)
                            }
                            val selectedBorder = if (activeNonOff) {
                                AppColors.WarningAmber
                            } else {
                                AppColors.PrimaryBlue
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
                                    containerColor = AppColors.BgElevated,
                                    labelColor = AppColors.TextSecondary,
                                    selectedContainerColor = selectedBg,
                                    selectedLabelColor = if (activeNonOff) AppColors.WarningAmber else AppColors.TextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = AppColors.Border,
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
                Text("Reset", color = AppColors.TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryBlue)
            ) {
                Text("Hotovo", color = Color.White)
            }
        }
    )
}
