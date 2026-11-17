package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.simulation.HudUiState
import com.example.ui.theme.HarborCyan
import com.example.ui.theme.HudBorderSubtle
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.LanternGold
import com.example.ui.theme.NightAbyss
import com.example.ui.theme.NightIndigoSurface
import com.example.ui.theme.ThreatCrimson
import com.example.ui.theme.VitalityLime
import com.example.ui.viewmodel.CloudSaveUiState

@Composable
fun TitleDeployOverlay(
  displayName: String,
  cloudSaveState: CloudSaveUiState,
  onStartNewRun: () -> Unit,
  onResumeCloudSave: () -> Unit,
  onOpenUpdates: () -> Unit,
  onSignOut: () -> Unit,
) {
  val saved = (cloudSaveState as? CloudSaveUiState.Loaded)?.save

  Box(modifier = Modifier.fillMaxSize().background(NightAbyss)) {
    Image(
      painter = painterResource(id = R.drawable.img_coastal_night_banner_1794873712284),
      contentDescription = "Costa Oscura coastal town",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.horizontalGradient(
              listOf(NightAbyss.copy(alpha = 0.94f), NightAbyss.copy(alpha = 0.72f), NightAbyss.copy(alpha = 0.35f)),
            ),
          ),
    )

    Row(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(28.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("WELCOME, ${displayName.uppercase()}", style = MaterialTheme.typography.labelMedium, color = HarborCyan)
        Text("POV SURVIVAL", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text(
          text = "Hold out in the night-time Spanish port of Costa Oscura. Enter Tienda & Panadería for supplies, watch your noise meter, and survive escalating zombie waves.",
          style = MaterialTheme.typography.bodyMedium,
          color = HudTextMuted,
          modifier = Modifier.widthIn(max = 420.dp),
        )
        if (saved != null) {
          Surface(
            color = NightIndigoSurface.copy(alpha = 0.88f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.border(1.dp, VitalityLime.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
          ) {
            Text(
              text = "CLOUD SAVE · WAVE ${saved.wave} · KILLS ${saved.totalKills} · BEST WAVE ${saved.highestWave}",
              style = MaterialTheme.typography.labelSmall,
              color = VitalityLime,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
          }
        }
      }

      Surface(
        color = NightIndigoSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier.width(300.dp)
            .border(1.5.dp, HudBorderSubtle, RoundedCornerShape(16.dp)),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (saved != null) {
            Button(
              onClick = onResumeCloudSave,
              colors = ButtonDefaults.buttonColors(containerColor = VitalityLime, contentColor = NightAbyss),
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("resume_cloud_button"),
            ) {
              Text("RESUME CLOUD SAVE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
          }
          Button(
            onClick = onStartNewRun,
            colors = ButtonDefaults.buttonColors(containerColor = LanternGold, contentColor = NightAbyss),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("deploy_new_run_button"),
          ) {
            Text("DEPLOY NEW RUN", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = onOpenUpdates,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_updates_button"),
          ) {
            Text("IN-APP UPDATES", style = MaterialTheme.typography.labelMedium, color = HarborCyan)
          }
          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
          ) {
            Text("SIGN OUT", style = MaterialTheme.typography.labelMedium, color = HudTextMuted)
          }
        }
      }
    }
  }
}

@Composable
fun PauseMenuOverlay(
  state: HudUiState,
  cloudStatus: String?,
  isMuted: Boolean,
  onResume: () -> Unit,
  onSaveCloud: () -> Unit,
  onLoadCloud: () -> Unit,
  onToggleDayNight: () -> Unit,
  onToggleMute: () -> Unit,
  onRestart: () -> Unit,
  onOpenUpdates: () -> Unit,
  onSignOut: () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(NightAbyss.copy(alpha = 0.84f))
        .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = NightIndigoSurface,
      shape = RoundedCornerShape(16.dp),
      modifier =
        Modifier.width(420.dp)
          .border(1.5.dp, HudBorderSubtle, RoundedCornerShape(16.dp)),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("PAUSED · WAVE ${state.wave}", style = MaterialTheme.typography.headlineMedium, color = LanternGold)
        if (cloudStatus != null) {
          Text(cloudStatus, style = MaterialTheme.typography.labelSmall, color = HarborCyan)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = onResume,
            colors = ButtonDefaults.buttonColors(containerColor = LanternGold, contentColor = NightAbyss),
            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_resume_button"),
          ) {
            Text("RESUME", fontWeight = FontWeight.Bold)
          }
          Button(
            onClick = onSaveCloud,
            colors = ButtonDefaults.buttonColors(containerColor = VitalityLime, contentColor = NightAbyss),
            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_save_cloud_button"),
          ) {
            Text("SAVE CLOUD", fontWeight = FontWeight.Bold)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = onLoadCloud,
            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_load_cloud_button"),
          ) {
            Text("LOAD CLOUD", color = HarborCyan)
          }
          OutlinedButton(
            onClick = onToggleDayNight,
            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_day_night_button"),
          ) {
            Text(if (state.dayMode) "NIGHT MODE" else "DAY MODE", color = Color.White)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = onToggleMute,
            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_mute_button"),
          ) {
            Text(if (isMuted) "UNMUTE SFX" else "MUTE SFX", color = Color.White)
          }
          OutlinedButton(
            onClick = onRestart,
            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_restart_button"),
          ) {
            Text("RESTART", color = ThreatCrimson)
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = onOpenUpdates,
            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_open_updates_button"),
          ) {
            Text("UPDATES", color = HarborCyan)
          }
          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_sign_out_button"),
          ) {
            Text("SIGN OUT", color = HudTextMuted)
          }
        }
      }
    }
  }
}

@Composable
fun DeathOverlay(
  state: HudUiState,
  onRetry: () -> Unit,
  onLoadCloud: () -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ThreatCrimson.copy(alpha = 0.28f))
        .background(NightAbyss.copy(alpha = 0.80f))
        .windowInsetsPadding(WindowInsets.safeDrawing),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = NightIndigoSurface,
      shape = RoundedCornerShape(16.dp),
      modifier =
        Modifier.width(380.dp)
          .border(2.dp, ThreatCrimson, RoundedCornerShape(16.dp)),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("YOU WERE OVERRUN", style = MaterialTheme.typography.headlineMedium, color = ThreatCrimson)
        Text(
          text = "Wave Reached: ${state.wave}  ·  Kills: ${state.totalKills}  ·  Best Wave: ${state.highestWave}",
          style = MaterialTheme.typography.labelMedium,
          color = Color.White,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
          onClick = onRetry,
          colors = ButtonDefaults.buttonColors(containerColor = LanternGold, contentColor = NightAbyss),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("death_retry_button"),
        ) {
          Text("RETRY WAVE 1", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
          onClick = onLoadCloud,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("death_load_cloud_button"),
        ) {
          Text("LOAD LAST CLOUD SAVE", color = HarborCyan)
        }
      }
    }
  }
}
