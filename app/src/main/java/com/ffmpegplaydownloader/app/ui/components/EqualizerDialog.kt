package com.ffmpegplaydownloader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.model.EqualizerPreset
import com.ffmpegplaydownloader.app.model.EqualizerState
import com.ffmpegplaydownloader.app.ui.theme.AppTheme
import java.util.Locale
import kotlin.math.abs

@Composable
fun EqualizerDialog(
    state: EqualizerState,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (EqualizerPreset) -> Unit,
    onBandLevelChange: (bandIndex: Int, levelMb: Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onLoudnessToggle: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    val colors = AppTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "🎚️ Equalizér & Zvuk",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (state.enabled) "Aktivní • ${state.preset.label}" else "Vypnuto (originální zvuk)",
                        color = if (state.enabled) colors.successGreen else colors.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = colors.primaryBlue,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.bgElevated,
                        uncheckedBorderColor = colors.border
                    )
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
                // Visual band curve preview
                EqualizerCurvePreview(state = state)

                // Preset chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EqualizerPreset.entries
                        .filter { it != EqualizerPreset.CUSTOM || state.preset == EqualizerPreset.CUSTOM }
                        .forEach { preset ->
                            val selected = state.preset == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectPreset(preset) },
                                label = { Text(preset.label, fontSize = 11.sp) },
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
                                modifier = Modifier.height(30.dp)
                            )
                        }
                }

                // Individual frequency bands
                val minF = state.minLevelMb.toFloat()
                val maxF = state.maxLevelMb.coerceAtLeast(state.minLevelMb + 100).toFloat()

                state.bands.forEach { band ->
                    val db = band.levelMb / 100.0
                    val dbText = String.format(Locale.US, "%+.1f dB", db)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                band.label,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                dbText,
                                color = when {
                                    !state.enabled -> colors.textSecondary
                                    band.levelMb > 0 -> colors.successGreen
                                    band.levelMb < 0 -> colors.warningAmber
                                    else -> colors.textSecondary
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Slider(
                            value = band.levelMb.toFloat().coerceIn(minF, maxF),
                            onValueChange = { raw ->
                                // Snap close to zero cleanly
                                val snapped = if (abs(raw) < 60f) 0 else raw.toInt()
                                onBandLevelChange(band.index, snapped)
                            },
                            valueRange = minF..maxF,
                            colors = SliderDefaults.colors(
                                thumbColor = if (state.enabled) colors.primaryBlue else colors.textSecondary,
                                activeTrackColor = if (state.enabled) colors.primaryBlue else colors.border,
                                inactiveTrackColor = colors.bgHover
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // Bass Boost hardware slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgElevated)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🎸 Bass Boost (hloubky)",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${(state.bassBoostStrength / 10)}%",
                            color = if (state.enabled && state.bassBoostStrength > 0) colors.youTubeMusicPink else colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = state.bassBoostStrength.toFloat(),
                        onValueChange = { onBassBoostChange(it.toInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.youTubeMusicPink,
                            activeTrackColor = colors.youTubeMusicPink,
                            inactiveTrackColor = if (colors.isDark) colors.bgMain else colors.border
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }

                // Loudness enhancer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = state.loudnessEnabled,
                        onCheckedChange = onLoudnessToggle,
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.primaryBlue,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedColor = colors.textSecondary
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "🔊 Zesílení a normalizace hlasitosti",
                        color = colors.textPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
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

@Composable
private fun EqualizerCurvePreview(state: EqualizerState) {
    val colors = AppTheme.colors
    val minMb = state.minLevelMb.toFloat()
    val maxMb = state.maxLevelMb.coerceAtLeast(state.minLevelMb + 100).toFloat()
    val span = (maxMb - minMb).coerceAtLeast(1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bgMain)
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        state.bands.forEach { band ->
            val norm = ((band.levelMb - minMb) / span).coerceIn(0.08f, 1f)
            val barColor = when {
                !state.enabled -> colors.border
                band.levelMb > 0 -> colors.primaryBlue
                band.levelMb < 0 -> colors.warningAmber
                else -> colors.textSecondary.copy(alpha = 0.6f)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight(norm)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    band.label.replace(" Hz", "").replace(" kHz", "k"),
                    color = colors.textSecondary,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
