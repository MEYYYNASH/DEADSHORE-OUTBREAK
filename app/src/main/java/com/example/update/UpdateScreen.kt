package com.example.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.HarborCyan
import com.example.ui.theme.HudBorderSubtle
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.LanternGold
import com.example.ui.theme.NightAbyss
import com.example.ui.theme.NightIndigoContainer
import com.example.ui.theme.NightIndigoSurface
import com.example.ui.theme.VitalityLime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpdateScreen(
  viewModel: UpdateViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val updateState by viewModel.updateState.collectAsStateWithLifecycle()
  val prefs by viewModel.preferences.collectAsStateWithLifecycle()

  var ownerInput by rememberSaveable { mutableStateOf(prefs.repoOwner) }
  var repoInput by rememberSaveable { mutableStateOf(prefs.repoName) }

  LaunchedEffect(prefs.repoOwner, prefs.repoName) {
    ownerInput = prefs.repoOwner
    repoInput = prefs.repoName
  }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(NightAbyss)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Top Header Bar
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("update_screen_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
          )
        }
        Column {
          Text("IN-APP SOFTWARE UPDATE", style = MaterialTheme.typography.titleLarge, color = LanternGold)
          Text(
            text = "Installed: v${VersionComparator.normalizeTag(UpdateConfig.currentVersionName)} (build ${UpdateConfig.currentVersionCode})",
            style = MaterialTheme.typography.labelMedium,
            color = HudTextMuted,
          )
        }
      }

      Surface(
        color = NightIndigoContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, HudBorderSubtle),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Icon(Icons.Filled.Verified, contentDescription = null, tint = VitalityLime, modifier = Modifier.size(16.dp))
          Text("FileProvider + SHA-256", style = MaterialTheme.typography.labelSmall, color = HudTextPrimary)
        }
      }
    }

    // Two-pane landscape layout
    Row(
      modifier = Modifier.fillMaxSize(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Left Configuration & Controls Pane
      Surface(
        color = NightIndigoSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, HudBorderSubtle),
        modifier = Modifier.weight(0.95f).fillMaxHeight(),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = HarborCyan, modifier = Modifier.size(20.dp))
            Text("GitHub Release Source", style = MaterialTheme.typography.titleMedium, color = HudTextPrimary)
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = ownerInput,
              onValueChange = { ownerInput = it },
              label = { Text("GitHub Owner") },
              singleLine = true,
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = HarborCyan,
                  unfocusedBorderColor = HudBorderSubtle,
                  focusedTextColor = HudTextPrimary,
                  unfocusedTextColor = HudTextPrimary,
                  focusedLabelColor = HarborCyan,
                  unfocusedLabelColor = HudTextMuted,
                ),
              modifier = Modifier.weight(1f).testTag("github_owner_input"),
            )
            OutlinedTextField(
              value = repoInput,
              onValueChange = { repoInput = it },
              label = { Text("Repository") },
              singleLine = true,
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = HarborCyan,
                  unfocusedBorderColor = HudBorderSubtle,
                  focusedTextColor = HudTextPrimary,
                  unfocusedTextColor = HudTextPrimary,
                  focusedLabelColor = HarborCyan,
                  unfocusedLabelColor = HudTextMuted,
                ),
              modifier = Modifier.weight(1f).testTag("github_repo_input"),
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = { viewModel.saveRepoConfig(ownerInput, repoInput) },
              modifier = Modifier.weight(1f).height(48.dp).testTag("save_repo_config_button"),
            ) {
              Text("SAVE & CHECK", color = HarborCyan)
            }
            Button(
              onClick = { viewModel.checkForUpdates(force = true, showDialogIfAvailable = false) },
              colors = ButtonDefaults.buttonColors(containerColor = LanternGold, contentColor = NightAbyss),
              modifier = Modifier.weight(1f).height(48.dp).testTag("check_updates_button"),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("CHECK NOW", fontWeight = FontWeight.Bold)
            }
          }

          val lastCheckedLabel =
            if (prefs.lastCheckedMs > 0L) {
              SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US).format(Date(prefs.lastCheckedMs))
            } else {
              "Never"
            }
          Text(
            text = "Last check: $lastCheckedLabel  ·  Cached tag: ${prefs.cachedLatestTag.ifBlank { "None" }}",
            style = MaterialTheme.typography.labelSmall,
            color = HudTextMuted,
          )
        }
      }

      // Right Live Update State Pane
      Box(
        modifier = Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()),
      ) {
        UpdateCard(
          state = updateState,
          onUpdateNow = { viewModel.startUpdateDownload() },
          onLater = null,
          onInstallNow = { viewModel.launchSystemInstaller() },
          onRetry = { viewModel.retry() },
          onCheckNow = { viewModel.checkForUpdates(force = true, showDialogIfAvailable = false) },
          buildPermissionIntent = { viewModel.buildPermissionSettingsIntent() },
          onReturnedFromPermissionSettings = { viewModel.onReturnedFromPermissionSettings() },
        )
      }
    }
  }
}
