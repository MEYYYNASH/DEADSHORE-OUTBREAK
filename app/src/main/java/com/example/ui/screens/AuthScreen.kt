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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.onGoogleSignInClicked
import com.example.ui.theme.HarborCyan
import com.example.ui.theme.HudBorderSubtle
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.LanternGold
import com.example.ui.theme.NightAbyss
import com.example.ui.theme.NightIndigoSurface
import com.example.ui.theme.ThreatCrimson
import com.example.ui.theme.VitalityLime

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(NightAbyss),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_coastal_night_banner_1794873712284),
      contentDescription = "Spanish coastal night town artwork",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    // Moody directional indigo scrim
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.horizontalGradient(
              colors =
                listOf(
                  NightAbyss.copy(alpha = 0.94f),
                  NightAbyss.copy(alpha = 0.78f),
                  NightAbyss.copy(alpha = 0.42f),
                ),
            ),
          ),
    )

    Row(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 28.dp, vertical = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1.1f).fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
      ) {
        Surface(
          color = LanternGold.copy(alpha = 0.16f),
          shape = RoundedCornerShape(6.dp),
          modifier = Modifier.border(1.dp, LanternGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
        ) {
          Text(
            text = "COSTA OSCURA · NIGHT SECTOR",
            style = MaterialTheme.typography.labelSmall,
            color = LanternGold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "POV SURVIVAL",
          style = MaterialTheme.typography.displaySmall,
          color = Color.White,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "First-person cel-shaded zombie survival in a Spanish coastal town. Scavenge Tienda & Panadería, manage food, water, and a 24-slot backpack, and hold out through escalating waves.",
          style = MaterialTheme.typography.bodyMedium,
          color = HudTextMuted,
          modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FeatureBadge(Icons.Default.Backpack, "24-Slot Pack", VitalityLime)
          FeatureBadge(Icons.Default.CloudSync, "Cloud Progress", HarborCyan)
          FeatureBadge(Icons.Default.Security, "60 FPS GLES3", LanternGold)
        }
      }

      Spacer(modifier = Modifier.width(24.dp))

      Surface(
        color = NightIndigoSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier.weight(0.85f)
            .widthIn(max = 340.dp)
            .border(1.5.dp, HudBorderSubtle, RoundedCornerShape(16.dp)),
      ) {
        Column(
          modifier = Modifier.padding(22.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "SURVIVOR DEPLOYMENT",
            style = MaterialTheme.typography.titleMedium,
            color = LanternGold,
          )
          Text(
            text =
              "Sign in with Google to sync your 24-slot backpack, equipped gear, and highest wave record.",
            style = MaterialTheme.typography.bodyMedium,
            color = HudTextMuted,
          )

          if (errorMsg != null) {
            Surface(
              color = ThreatCrimson.copy(alpha = 0.16f),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth().border(1.dp, ThreatCrimson, RoundedCornerShape(8.dp)),
            ) {
              Text(
                text = errorMsg.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = ThreatCrimson,
                modifier = Modifier.padding(10.dp),
              )
            }
          }

          Button(
            onClick = {
              errorMsg = null
              isLoading = true
              onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMsg = msg
                },
                scope = scope,
                onAuthCancelled = { isLoading = false },
              )
            },
            enabled = !isLoading,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = LanternGold,
                contentColor = NightAbyss,
              ),
            shape = RoundedCornerShape(10.dp),
            modifier =
              Modifier.fillMaxWidth()
                .height(50.dp)
                .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = NightAbyss,
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = "Sign in icon",
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "SIGN IN WITH GOOGLE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FeatureBadge(icon: ImageVector, label: String, accent: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp))
        .background(NightIndigoSurface.copy(alpha = 0.85f))
        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(14.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
  }
}
