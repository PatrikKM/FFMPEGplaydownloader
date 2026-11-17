package com.ffmpegplaydownloader.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ffmpegplaydownloader.app.data.CookiesStatus
import com.ffmpegplaydownloader.app.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CookiesSettingsDialog(
    status: CookiesStatus,
    feedbackMessage: String?,
    onImportUri: (Uri) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onClearCookies: () -> Unit,
    onDismiss: () -> Unit
) {
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportUri(uri)
        }
    }

    val statusText = when {
        status.hasFile && status.enabled -> "Aktivní"
        status.hasFile && !status.enabled -> "Pozastaveno"
        else -> "Nenahráno"
    }
    val statusColor = when {
        status.hasFile && status.enabled -> AppColors.SuccessGreen
        status.hasFile && !status.enabled -> AppColors.WarningAmber
        else -> AppColors.TextSecondary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.BgSurface,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "🍪 Cookies (yt-dlp)",
                    color = AppColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Volitelné ověření relace pro yt-dlp",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Můžete importovat vlastní soubor cookies.txt ve formátu Netscape z prohlížeče s ověřeným věkem. " +
                        "Cookies slouží pouze k autentizaci požadavků yt-dlp (přehrávání, stahování a přímé URL), " +
                        "nemění ostatní chování aplikace a zůstávají uloženy výhradně lokálně v soukromém úložišti aplikace.",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                // Current status card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.BgElevated)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Stav cookies",
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (status.hasFile) {
                        Text(
                            text = "Soubor: ${status.fileName.ifBlank { "cookies.txt" }}",
                            color = AppColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val importedText = if (status.importedAt > 0L) {
                            val fmt = SimpleDateFormat("d. M. yyyy HH:mm", Locale.getDefault())
                            " • Nahráno ${fmt.format(Date(status.importedAt))}"
                        } else {
                            ""
                        }
                        Text(
                            text = "Záznamů: ${status.entryCount}$importedText",
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Žádný soubor cookies.txt není nahraný.",
                            color = AppColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Enable / Pause switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.BgElevated)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Používat cookies při přehrávání a stahování",
                        color = if (status.hasFile) AppColors.TextPrimary else AppColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Switch(
                        checked = status.hasFile && status.enabled,
                        onCheckedChange = onToggleEnabled,
                        enabled = status.hasFile,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppColors.SuccessGreen,
                            uncheckedThumbColor = AppColors.TextSecondary,
                            uncheckedTrackColor = AppColors.BgSurface
                        )
                    )
                }

                // Optional feedback / error banner
                if (!feedbackMessage.isNullOrBlank()) {
                    val isError = feedbackMessage.startsWith("❌") ||
                        feedbackMessage.contains("Chyba", ignoreCase = true) ||
                        feedbackMessage.contains("Neplatný", ignoreCase = true) ||
                        feedbackMessage.contains("Nepodařilo", ignoreCase = true) ||
                        feedbackMessage.contains("prázdný", ignoreCase = true) ||
                        feedbackMessage.contains("velký", ignoreCase = true)
                    val accent = when {
                        isError -> AppColors.AccentRed
                        feedbackMessage.startsWith("✅") -> AppColors.SuccessGreen
                        else -> AppColors.WarningAmber
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = feedbackMessage,
                            color = AppColors.TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Import & Remove actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            openDocumentLauncher.launch(
                                arrayOf("text/*", "application/octet-stream", "*/*")
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📂 Nahrát cookies.txt", color = Color.White, fontSize = 13.sp)
                    }

                    if (status.hasFile) {
                        OutlinedButton(
                            onClick = onClearCookies,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            border = BorderStroke(
                                1.dp,
                                AppColors.AccentRed.copy(alpha = 0.65f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🗑 Odstranit", color = AppColors.AccentRed, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.BgElevated)
            ) {
                Text("Zavřít", color = AppColors.TextPrimary)
            }
        }
    )
}
