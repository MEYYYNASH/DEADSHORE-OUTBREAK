package com.example.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HarborCyan
import com.example.ui.theme.HudBorderSubtle
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HungerAmber
import com.example.ui.theme.LanternGold
import com.example.ui.theme.NightAbyss
import com.example.ui.theme.NightIndigoContainer
import com.example.ui.theme.NightIndigoSurface
import com.example.ui.theme.ThreatCrimson
import com.example.ui.theme.VitalityLime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateCard(
  state: UpdateState,
  onUpdateNow: () -> Unit,
  onLater: (() -> Unit)?,
  onInstallNow: () -> Unit,
  onRetry: () -> Unit,
  onCheckNow: () -> Unit,
  buildPermissionIntent: () -> android.content.Intent,
  onReturnedFromPermissionSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      onReturnedFromPermissionSettings()
    }

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = NightIndigoSurface),
    border = BorderStroke(1.5.dp, HudBorderSubtle),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    AnimatedContent(
      targetState = state,
      label = "update_card_state",
      modifier = Modifier.fillMaxWidth().padding(20.dp),
    ) { currentState ->
      when (currentState) {
        UpdateState.Idle -> {
          IdleUpdateContent(onCheckNow = onCheckNow)
        }
        UpdateState.Checking -> {
          CheckingUpdateContent()
        }
        is UpdateState.UpToDate -> {
          UpToDateContent(state = currentState, onCheckNow = onCheckNow)
        }
        is UpdateState.UpdateAvailable -> {
          UpdateAvailableContent(
            state = currentState,
            onUpdateNow = onUpdateNow,
            onLater = onLater,
          )
        }
        is UpdateState.Downloading -> {
          DownloadingUpdateContent(state = currentState)
        }
        is UpdateState.Verifying -> {
          VerifyingUpdateContent(state = currentState)
        }
        is UpdateState.ReadyToInstall -> {
          ReadyToInstallContent(
            state = currentState,
            onInstallNow = onInstallNow,
          )
        }
        is UpdateState.PermissionRequired -> {
          PermissionRequiredContent(
            state = currentState,
            onOpenSettings = {
              permissionLauncher.launch(buildPermissionIntent())
            },
          )
        }
        is UpdateState.Error -> {
          ErrorUpdateContent(
            state = currentState,
            onRetry = onRetry,
            onDismiss = onLater,
          )
        }
      }
    }
  }
}

@Composable
private fun IdleUpdateContent(onCheckNow: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = HarborCyan)
      Text("GitHub Release Updater", style = MaterialTheme.typography.titleMedium, color = HudTextPrimary)
    }
    Text(
      text = "Installed version: v${VersionComparator.normalizeTag(UpdateConfig.currentVersionName)}",
      style = MaterialTheme.typography.bodyMedium,
      color = HudTextMuted,
    )
    Button(
      onClick = onCheckNow,
      colors = ButtonDefaults.buttonColors(containerColor = HarborCyan, contentColor = NightAbyss),
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("idle_check_updates_button"),
    ) {
      Text("Check for Updates", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun CheckingUpdateContent() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
  ) {
    CircularProgressIndicator(
      color = HarborCyan,
      strokeWidth = 3.dp,
      modifier = Modifier.size(32.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Checking for Updates...", style = MaterialTheme.typography.titleMedium, color = HudTextPrimary)
      Text(
        "Querying latest published GitHub Release",
        style = MaterialTheme.typography.bodySmall,
        color = HudTextMuted,
      )
    }
  }
}

@Composable
private fun UpToDateContent(
  state: UpdateState.UpToDate,
  onCheckNow: () -> Unit,
) {
  val formattedDate = remember(state.lastCheckedMs) { formatTimestamp(state.lastCheckedMs) }
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VitalityLime, modifier = Modifier.size(28.dp))
      Column {
        Text("You're Up to Date", style = MaterialTheme.typography.titleMedium, color = VitalityLime)
        Text(
          text = "Installed: v${VersionComparator.normalizeTag(state.currentVersion)}",
          style = MaterialTheme.typography.bodyMedium,
          color = HudTextPrimary,
        )
      }
    }
    if (state.latestTag != null) {
      Text(
        text =
          if (state.isOlderOnRemote) {
            "Latest GitHub release is ${state.latestTag} (downgrade prevented)."
          } else {
            "Latest GitHub release: ${state.latestTag}"
          },
        style = MaterialTheme.typography.bodySmall,
        color = HudTextMuted,
      )
    }
    if (formattedDate.isNotEmpty()) {
      Text(
        text = "Last checked: $formattedDate",
        style = MaterialTheme.typography.labelSmall,
        color = HudTextMuted,
      )
    }
    OutlinedButton(
      onClick = onCheckNow,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("recheck_updates_button"),
    ) {
      Text("Check Again", color = HarborCyan)
    }
  }
}

@Composable
private fun UpdateAvailableContent(
  state: UpdateState.UpdateAvailable,
  onUpdateNow: () -> Unit,
  onLater: (() -> Unit)?,
) {
  val releaseNotes = remember(state.release.body) { parseReleaseBullets(state.release.body) }
  val publishedDate = remember(state.release.publishedAt) { formatIsoDate(state.release.publishedAt) }
  val formattedSize = remember(state.apkAsset.sizeBytes) { ApkDownloader.formatMb(state.apkAsset.sizeBytes) }
  val currentDisplay = "v${VersionComparator.normalizeTag(state.currentVersion)}"

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Filled.NewReleases, contentDescription = null, tint = LanternGold, modifier = Modifier.size(28.dp))
        Column {
          Text("New Version Available", style = MaterialTheme.typography.titleMedium, color = LanternGold)
          Text(
            text = state.release.tagName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
        }
      }

      Surface(
        color = NightIndigoContainer,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (state.shaAsset != null) VitalityLime.copy(alpha = 0.5f) else HudBorderSubtle),
      ) {
        Text(
          text = if (state.shaAsset != null) "SHA-256" else "APK",
          style = MaterialTheme.typography.labelSmall,
          color = if (state.shaAsset != null) VitalityLime else HarborCyan,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }

    Surface(
      color = NightIndigoContainer,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Current: $currentDisplay", style = MaterialTheme.typography.labelMedium, color = HudTextMuted)
        Text("Size: $formattedSize", style = MaterialTheme.typography.labelMedium, color = HarborCyan)
      }
    }

    if (publishedDate.isNotEmpty()) {
      Text("Published: $publishedDate", style = MaterialTheme.typography.labelSmall, color = HudTextMuted)
    }

    HorizontalDivider(color = HudBorderSubtle)

    Text("What's New", style = MaterialTheme.typography.labelLarge, color = HudTextPrimary)

    Column(
      modifier = Modifier.fillMaxWidth().height(92.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      releaseNotes.forEach { bullet ->
        Text(
          text = "• $bullet",
          style = MaterialTheme.typography.bodySmall,
          color = HudTextMuted,
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (onLater != null) {
        OutlinedButton(
          onClick = onLater,
          modifier = Modifier.weight(1f).height(48.dp).minimumInteractiveComponentSize().testTag("update_later_button"),
        ) {
          Text("Later", color = HudTextMuted)
        }
      }
      Button(
        onClick = onUpdateNow,
        colors = ButtonDefaults.buttonColors(containerColor = LanternGold, contentColor = NightAbyss),
        modifier = Modifier.weight(1.4f).height(48.dp).minimumInteractiveComponentSize().testTag("update_now_button"),
      ) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Update Now", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun DownloadingUpdateContent(state: UpdateState.Downloading) {
  val pct = (state.progress * 100f).toInt().coerceIn(0, 100)
  val downloadedLabel = ApkDownloader.formatMb(state.bytesDownloaded)
  val totalLabel = if (state.totalBytes > 0L) ApkDownloader.formatMb(state.totalBytes) else "Unknown"

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Downloading Update...", style = MaterialTheme.typography.titleMedium, color = HarborCyan)
      Text(
        text = if (state.totalBytes > 0L) "$pct%" else "...",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
    }

    if (state.totalBytes > 0L) {
      LinearProgressIndicator(
        progress = { state.progress },
        color = HarborCyan,
        trackColor = NightIndigoContainer,
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).testTag("download_progress_bar"),
      )
    } else {
      LinearProgressIndicator(
        color = HarborCyan,
        trackColor = NightIndigoContainer,
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).testTag("download_progress_bar"),
      )
    }

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("$downloadedLabel / $totalLabel", style = MaterialTheme.typography.labelMedium, color = HudTextMuted)
      Text(state.release.tagName, style = MaterialTheme.typography.labelMedium, color = LanternGold)
    }
  }
}

@Composable
private fun VerifyingUpdateContent(state: UpdateState.Verifying) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
  ) {
    CircularProgressIndicator(
      color = LanternGold,
      strokeWidth = 3.dp,
      modifier = Modifier.size(32.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Verifying Update...", style = MaterialTheme.typography.titleMedium, color = LanternGold)
      Text(
        "Validating SHA-256 checksum & APK package signature for ${state.release.tagName}",
        style = MaterialTheme.typography.bodySmall,
        color = HudTextMuted,
      )
    }
  }
}

@Composable
private fun ReadyToInstallContent(
  state: UpdateState.ReadyToInstall,
  onInstallNow: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = VitalityLime, modifier = Modifier.size(28.dp))
      Column {
        Text("Verification Successful", style = MaterialTheme.typography.titleMedium, color = VitalityLime)
        Text("Ready to Install · ${state.release.tagName}", style = MaterialTheme.typography.bodyMedium, color = HudTextPrimary)
      }
    }

    Surface(
      color = NightIndigoContainer,
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = if (state.sha256Verified) "SHA-256 Checksum Verified" else "Package Integrity Verified (No .sha256 asset in release)",
          style = MaterialTheme.typography.labelMedium,
          color = if (state.sha256Verified) VitalityLime else HungerAmber,
        )
        Text(
          text = "SHA-256: ${state.computedSha256.take(24)}...",
          style = MaterialTheme.typography.labelSmall,
          color = HudTextMuted,
        )
      }
    }

    Button(
      onClick = onInstallNow,
      colors = ButtonDefaults.buttonColors(containerColor = VitalityLime, contentColor = NightAbyss),
      modifier = Modifier.fillMaxWidth().height(48.dp).minimumInteractiveComponentSize().testTag("install_update_button"),
    ) {
      Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Install Update", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun PermissionRequiredContent(
  state: UpdateState.PermissionRequired,
  onOpenSettings: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(Icons.Filled.Security, contentDescription = null, tint = HungerAmber, modifier = Modifier.size(28.dp))
      Column {
        Text("Permission Required", style = MaterialTheme.typography.titleMedium, color = HungerAmber)
        Text("Allow from this source", style = MaterialTheme.typography.bodyMedium, color = HudTextPrimary)
      }
    }
    Text(
      text = "Update ${state.release.tagName} is verified and ready. Enable \"Allow from this source\" in Android Settings so the system Package Installer can complete installation.",
      style = MaterialTheme.typography.bodySmall,
      color = HudTextMuted,
    )
    Button(
      onClick = onOpenSettings,
      colors = ButtonDefaults.buttonColors(containerColor = LanternGold, contentColor = NightAbyss),
      modifier = Modifier.fillMaxWidth().height(48.dp).minimumInteractiveComponentSize().testTag("open_install_permission_settings_button"),
    ) {
      Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Allow in Settings", fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun ErrorUpdateContent(
  state: UpdateState.Error,
  onRetry: () -> Unit,
  onDismiss: (() -> Unit)?,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ThreatCrimson, modifier = Modifier.size(28.dp))
      Text(state.title, style = MaterialTheme.typography.titleMedium, color = ThreatCrimson)
    }
    Text(state.message, style = MaterialTheme.typography.bodySmall, color = HudTextPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      if (onDismiss != null) {
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier.weight(1f).height(48.dp).testTag("update_error_dismiss_button"),
        ) {
          Text("Close", color = HudTextMuted)
        }
      }
      if (state.canRetry) {
        Button(
          onClick = onRetry,
          colors = ButtonDefaults.buttonColors(containerColor = HarborCyan, contentColor = NightAbyss),
          modifier = Modifier.weight(1.2f).height(48.dp).minimumInteractiveComponentSize().testTag("update_retry_button"),
        ) {
          Text("Retry", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

private fun parseReleaseBullets(body: String): List<String> {
  val lines =
    body.lines()
      .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
      .filter { it.isNotBlank() && !it.startsWith("#") }
  return if (lines.isEmpty()) {
    listOf("New features", "Performance improvements", "Bug fixes")
  } else {
    lines.take(8)
  }
}

private fun formatTimestamp(ms: Long): String {
  if (ms <= 0L) return ""
  return SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(ms))
}

private fun formatIsoDate(iso: String): String {
  if (iso.isBlank()) return ""
  return iso.substringBefore('T')
}
