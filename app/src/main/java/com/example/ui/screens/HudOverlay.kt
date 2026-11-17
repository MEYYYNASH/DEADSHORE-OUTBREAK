package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ItemCatalog
import com.example.game.simulation.GameEngine
import com.example.game.simulation.HudUiState
import com.example.game.world.TownWorld
import com.example.ui.theme.HarborCyan
import com.example.ui.theme.HudBorderSubtle
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.HungerAmber
import com.example.ui.theme.LanternGold
import com.example.ui.theme.NightAbyss
import com.example.ui.theme.NightIndigoSurface
import com.example.ui.theme.ThreatCrimson
import com.example.ui.theme.VitalityLime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun HudOverlay(
  state: HudUiState,
  engine: GameEngine,
  world: TownWorld,
  onOpenBag: () -> Unit,
  onOpenPause: () -> Unit,
  onToggleDayNight: () -> Unit,
  onToggleFlashlight: () -> Unit,
) {
  Box(modifier = Modifier.fillMaxSize()) {
    // 1. Right-half free look touch zone (behind buttons)
    Box(
      modifier =
        Modifier.fillMaxHeight()
          .fillMaxWidth(0.55f)
          .align(Alignment.CenterEnd)
          .pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              engine.addLookDelta(dragAmount.x, dragAmount.y)
            }
          },
    )

    // 2. Holo / Red-Dot Sight ADS Overlay + Crosshair + Hitmarker + Damage Vignette
    ReticleAndAdsOverlay(state)

    if (state.damageFlash > 0.02f) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.radialGradient(
                colors =
                  listOf(
                    Color.Transparent,
                    ThreatCrimson.copy(alpha = state.damageFlash * 0.48f),
                  ),
              ),
            ),
      )
    }

    // 3. Safe-area HUD chrome
    Box(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      // TOP LEFT: Minimap + Health / Food / Water Bars
      Column(
        modifier = Modifier.align(Alignment.TopStart),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.Top,
        ) {
          MinimapRadar(state = state, world = world)
          VitalsPanel(state = state)
        }
      }

      // TOP CENTER: Compass Strip + Pickup Toast
      Column(
        modifier = Modifier.align(Alignment.TopCenter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        CompassStrip(
          yawDeg = state.playerYawDeg,
          supplyBearingDeg = state.supplyDropBearingDeg,
        )
        AnimatedVisibility(
          visible = state.toastMessage.isNotEmpty(),
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          Surface(
            color = NightIndigoSurface.copy(alpha = 0.90f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.border(1.dp, LanternGold.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
          ) {
            Text(
              text = state.toastMessage,
              style = MaterialTheme.typography.labelMedium,
              color = LanternGold,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
          }
        }
      }

      // TOP RIGHT: Objective Tracker + Noise Meter + Quick Icons
      Column(
        modifier = Modifier.align(Alignment.TopEnd),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ObjectiveCard(state = state)
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              SmallHudIconBtn(
                tag = "flashlight_button",
                icon = Icons.Default.FlashlightOn,
                desc = "Toggle Flashlight",
                active = state.flashlightOn,
                onClick = onToggleFlashlight,
              )
              SmallHudIconBtn(
                tag = "day_night_button",
                icon = Icons.Default.WbSunny,
                desc = "Toggle Day/Night",
                active = state.dayMode,
                onClick = onToggleDayNight,
              )
              SmallHudIconBtn(
                tag = "pause_button",
                icon = Icons.Default.Pause,
                desc = "Pause Menu",
                active = false,
                onClick = onOpenPause,
              )
            }
            NoiseMeterBar(noise = state.noiseLevel)
          }
        }
      }

      // BOTTOM LEFT: Virtual Movement Joystick + Sprint
      Row(
        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp, start = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        VirtualJoystick(
          onMove = { ix, iy ->
            engine.moveInputX = ix
            engine.moveInputY = iy
          },
        )
        HoldActionButton(
          tag = "sprint_button",
          label = "SPRINT",
          icon = Icons.Default.DirectionsRun,
          active = state.isSprinting,
          accent = VitalityLime,
          sizeDp = 54,
          onPressChange = { pressed -> engine.sprintHeld = pressed },
        )
      }

      // BOTTOM CENTER: 6-Slot Hotbar with Ammo Counts
      HotbarStrip(
        state = state,
        onSelectSlot = { slot -> engine.selectHotbarSlot(slot) },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
      )

      // BOTTOM RIGHT: FIRE / AIM (ADS) / RELOAD / BAG
      Box(
        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp, end = 4.dp),
      ) {
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TacticalTapButton(
              tag = "bag_button",
              label = "BAG",
              icon = Icons.Default.Backpack,
              accent = LanternGold,
              active = false,
              sizeDp = 54,
              onClick = onOpenBag,
            )
            TacticalTapButton(
              tag = "reload_button",
              label = "RELOAD",
              icon = Icons.Default.Refresh,
              accent = HarborCyan,
              active = state.isReloading,
              sizeDp = 54,
              onClick = { engine.startReload() },
            )
            TacticalTapButton(
              tag = "aim_button",
              label = "AIM",
              icon = Icons.Default.CenterFocusStrong,
              accent = ThreatCrimson,
              active = state.isAds,
              sizeDp = 58,
              onClick = { engine.toggleAds() },
            )
          }

          HoldActionButton(
            tag = "fire_button",
            label = "FIRE",
            icon = Icons.Default.Bolt,
            active = false,
            accent = ThreatCrimson,
            sizeDp = 76,
            onPressChange = { pressed ->
              engine.fireHeld = pressed
              if (!pressed) engine.releaseFireTrigger()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun ReticleAndAdsOverlay(state: HudUiState) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f

    if (state.isAds) {
      // Holo / Red-Dot Sight Housing + Vignette
      drawCircle(
        color = Color.Black.copy(alpha = 0.42f),
        radius = size.minDimension * 0.62f,
        style = Stroke(width = size.minDimension * 0.44f),
      )
      val opticR = 68.dp.toPx()
      drawCircle(
        color = NightAbyss.copy(alpha = 0.85f),
        radius = opticR,
        style = Stroke(width = 6.dp.toPx()),
      )
      drawCircle(
        color = HarborCyan.copy(alpha = 0.25f),
        radius = opticR - 4.dp.toPx(),
        style = Stroke(width = 1.5.dp.toPx()),
      )
      // Holo reticle ticks + glowing red dot
      val tickLen = 14.dp.toPx()
      val gap = 16.dp.toPx()
      val red = ThreatCrimson
      drawLine(red, Offset(cx - gap - tickLen, cy), Offset(cx - gap, cy), strokeWidth = 2.dp.toPx())
      drawLine(red, Offset(cx + gap, cy), Offset(cx + gap + tickLen, cy), strokeWidth = 2.dp.toPx())
      drawLine(red, Offset(cx, cy - gap - tickLen), Offset(cx, cy - gap), strokeWidth = 2.dp.toPx())
      drawLine(red, Offset(cx, cy + gap), Offset(cx, cy + gap + tickLen), strokeWidth = 2.dp.toPx())
      drawCircle(color = Color(0xFFFF3838), radius = 3.5.dp.toPx(), center = Offset(cx, cy))
    } else {
      val gap = 8.dp.toPx()
      val len = 8.dp.toPx()
      val col = Color.White.copy(alpha = 0.82f)
      drawLine(col, Offset(cx - gap - len, cy), Offset(cx - gap, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
      drawLine(col, Offset(cx + gap, cy), Offset(cx + gap + len, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
      drawLine(col, Offset(cx, cy - gap - len), Offset(cx, cy - gap), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
      drawLine(col, Offset(cx, cy + gap), Offset(cx, cy + gap + len), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }

    if (state.hitMarkerFlash > 0.05f) {
      val d1 = 6.dp.toPx()
      val d2 = 15.dp.toPx()
      val c = LanternGold.copy(alpha = state.hitMarkerFlash)
      drawLine(c, Offset(cx - d2, cy - d2), Offset(cx - d1, cy - d1), strokeWidth = 2.5.dp.toPx())
      drawLine(c, Offset(cx + d1, cy - d1), Offset(cx + d2, cy - d2), strokeWidth = 2.5.dp.toPx())
      drawLine(c, Offset(cx - d2, cy + d2), Offset(cx - d1, cy + d1), strokeWidth = 2.5.dp.toPx())
      drawLine(c, Offset(cx + d1, cy + d1), Offset(cx + d2, cy + d2), strokeWidth = 2.5.dp.toPx())
    }

    if (state.isReloading) {
      val r = 24.dp.toPx()
      drawArc(
        color = HarborCyan,
        startAngle = -90f,
        sweepAngle = 360f * state.reloadProgress,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2f, r * 2f),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
      )
    }
  }
}

@Composable
private fun CompassStrip(yawDeg: Float, supplyBearingDeg: Float?) {
  Surface(
    color = NightIndigoSurface.copy(alpha = 0.80f),
    shape = RoundedCornerShape(10.dp),
    modifier =
      Modifier.width(270.dp)
        .height(28.dp)
        .border(1.dp, HudBorderSubtle, RoundedCornerShape(10.dp)),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height
      val cx = w * 0.5f
      val labels =
        listOf(
          0f to "N",
          45f to "NE",
          90f to "E",
          135f to "SE",
          180f to "S",
          225f to "SW",
          270f to "W",
          315f to "NW",
        )
      val pxPerDeg = 2.1f
      for (deg in 0 until 360 step 15) {
        var diff = deg - yawDeg
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        if (kotlin.math.abs(diff) < 62f) {
          val x = cx + diff * pxPerDeg
          val isMajor = deg % 45 == 0
          drawLine(
            color = if (isMajor) LanternGold else HudTextMuted.copy(alpha = 0.55f),
            start = Offset(x, if (isMajor) 4f else 9f),
            end = Offset(x, h - 4f),
            strokeWidth = if (isMajor) 2.5f else 1.5f,
          )
        }
      }
      if (supplyBearingDeg != null) {
        var diff = supplyBearingDeg - yawDeg
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        val x = (cx + diff * pxPerDeg).coerceIn(12f, w - 12f)
        drawCircle(color = HarborCyan, radius = 5.dp.toPx(), center = Offset(x, h * 0.5f))
      }
      // Center heading caret
      drawLine(LanternGold, Offset(cx, 0f), Offset(cx, 7.dp.toPx()), strokeWidth = 2.5.dp.toPx())
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      val dir =
        when (((yawDeg + 22.5f) % 360f).toInt() / 45) {
          0 -> "N"
          1 -> "NE"
          2 -> "E"
          3 -> "SE"
          4 -> "S"
          5 -> "SW"
          6 -> "W"
          else -> "NW"
        }
      Text(
        text = "$dir · ${yawDeg.toInt()}°",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier =
          Modifier.background(NightAbyss.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
      )
    }
  }
}

@Composable
private fun VitalsPanel(state: HudUiState) {
  Surface(
    color = NightIndigoSurface.copy(alpha = 0.82f),
    shape = RoundedCornerShape(10.dp),
    modifier =
      Modifier.width(142.dp)
        .border(1.dp, HudBorderSubtle, RoundedCornerShape(10.dp)),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      VitalMeter("HP", state.health / 100f, VitalityLime, "${state.health.toInt()}")
      VitalMeter("FOOD", state.food / 100f, HungerAmber, "${state.food.toInt()}%")
      VitalMeter("H2O", state.water / 100f, HarborCyan, "${state.water.toInt()}%")
      VitalMeter("STAM", state.stamina / 100f, LanternGold, "${state.stamina.toInt()}%")
    }
  }
}

@Composable
private fun VitalMeter(label: String, fraction: Float, color: Color, valueText: String) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = HudTextMuted, modifier = Modifier.width(30.dp))
    Box(
      modifier =
        Modifier.weight(1f)
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(NightAbyss),
    ) {
      Box(
        modifier =
          Modifier.fillMaxHeight()
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .background(color),
      )
    }
    Text(valueText, style = MaterialTheme.typography.labelSmall, color = Color.White, fontSize = 9.sp)
  }
}

@Composable
private fun MinimapRadar(state: HudUiState, world: TownWorld) {
  Surface(
    color = NightAbyss.copy(alpha = 0.86f),
    shape = RoundedCornerShape(10.dp),
    modifier =
      Modifier.size(84.dp)
        .border(1.5.dp, HudBorderSubtle, RoundedCornerShape(10.dp)),
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
      val cx = size.width * 0.5f
      val cy = size.height * 0.5f
      val scale = size.width / 80f

      // Draw houses relative to player
      for (h in world.houses) {
        val left = cx + (h.minX - state.playerX) * scale
        val top = cy + (h.minZ - state.playerZ) * scale
        val w = (h.maxX - h.minX) * scale
        val hh = (h.maxZ - h.minZ) * scale
        drawRect(
          color = Color(0xFF474270),
          topLeft = Offset(left, top),
          size = Size(w, hh),
        )
      }

      // Draw loot & supply drop
      for (l in state.minimapLoot) {
        val lx = cx + (l.x - state.playerX) * scale
        val lz = cy + (l.z - state.playerZ) * scale
        if (lx in 0f..size.width && lz in 0f..size.height) {
          drawCircle(
            color = if (l.isSupplyDrop) HarborCyan else LanternGold,
            radius = if (l.isSupplyDrop) 3.8f else 2.2f,
            center = Offset(lx, lz),
          )
        }
      }

      // Draw zombies
      for (z in state.minimapZombies) {
        val zx = cx + (z.x - state.playerX) * scale
        val zz = cy + (z.z - state.playerZ) * scale
        if (zx in 0f..size.width && zz in 0f..size.height) {
          drawCircle(
            color = if (z.isArmed) Color(0xFFFF6B35) else ThreatCrimson,
            radius = 2.6f,
            center = Offset(zx, zz),
          )
        }
      }

      // Player dot + look direction line
      val yawRad = state.playerYawDeg * (PI.toFloat() / 180f)
      val fx = sin(yawRad) * 10f
      val fz = -cos(yawRad) * 10f
      drawLine(VitalityLime, Offset(cx, cy), Offset(cx + fx, cy + fz), strokeWidth = 2f)
      drawCircle(color = Color.White, radius = 3.2f, center = Offset(cx, cy))
    }
  }
}

@Composable
private fun ObjectiveCard(state: HudUiState) {
  Surface(
    color = NightIndigoSurface.copy(alpha = 0.85f),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.border(1.dp, HudBorderSubtle, RoundedCornerShape(10.dp)),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
      Text(
        text = "WAVE ${state.wave}",
        style = MaterialTheme.typography.titleMedium,
        color = LanternGold,
      )
      Text(
        text =
          if (state.isRestPhase) "NEXT WAVE IN ${state.restTimerSec.toInt() + 1}s · LOOT DROP"
          else "HOSTILES: ${state.zombiesRemaining} · KILLS: ${state.totalKills}",
        style = MaterialTheme.typography.labelSmall,
        color = if (state.isRestPhase) HarborCyan else Color.White,
      )
    }
  }
}

@Composable
private fun NoiseMeterBar(noise: Float) {
  Surface(
    color = NightIndigoSurface.copy(alpha = 0.82f),
    shape = RoundedCornerShape(8.dp),
    modifier =
      Modifier.width(120.dp)
        .border(1.dp, HudBorderSubtle, RoundedCornerShape(8.dp)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(Icons.Default.VolumeUp, contentDescription = "Noise meter", tint = HudTextMuted, modifier = Modifier.size(14.dp))
      Box(
        modifier =
          Modifier.weight(1f)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(NightAbyss),
      ) {
        val barCol = if (noise > 0.6f) ThreatCrimson else if (noise > 0.25f) HungerAmber else VitalityLime
        Box(
          modifier =
            Modifier.fillMaxHeight()
              .fillMaxWidth(noise.coerceIn(0.05f, 1f))
              .background(barCol),
        )
      }
    }
  }
}

@Composable
private fun SmallHudIconBtn(
  tag: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  desc: String,
  active: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    color = if (active) LanternGold.copy(alpha = 0.24f) else NightIndigoSurface.copy(alpha = 0.84f),
    shape = RoundedCornerShape(8.dp),
    modifier =
      Modifier.size(36.dp)
        .border(1.dp, if (active) LanternGold else HudBorderSubtle, RoundedCornerShape(8.dp)),
  ) {
    IconButton(onClick = onClick, modifier = Modifier.fillMaxSize().testTag(tag)) {
      Icon(icon, contentDescription = desc, tint = if (active) LanternGold else Color.White, modifier = Modifier.size(18.dp))
    }
  }
}

@Composable
private fun HotbarStrip(
  state: HudUiState,
  onSelectSlot: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    for (i in 0 until 6) {
      val itemId = state.hotbarSlots.getOrNull(i).orEmpty()
      val def = ItemCatalog.get(itemId)
      val selected = i == state.activeHotbarSlot
      val ammoText =
        when (itemId) {
          "m4_carbine" -> "${if (selected) state.magAmmo else "-"}/${state.reserveAmmo.takeIf { selected } ?: ""}".trimEnd('/')
          "glock_17" -> "${if (selected) state.magAmmo else "-"}/${state.reserveAmmo.takeIf { selected } ?: ""}".trimEnd('/')
          else -> {
            val count = state.backpack.filter { it.itemId == itemId }.sumOf { it.quantity }
            if (count > 1) "x$count" else ""
          }
        }
      Surface(
        color = if (selected) LanternGold.copy(alpha = 0.24f) else NightIndigoSurface.copy(alpha = 0.86f),
        shape = RoundedCornerShape(8.dp),
        modifier =
          Modifier.size(width = 56.dp, height = 48.dp)
            .border(if (selected) 2.dp else 1.dp, if (selected) LanternGold else HudBorderSubtle, RoundedCornerShape(8.dp))
            .clickable { onSelectSlot(i) }
            .testTag("hotbar_slot_$i"),
      ) {
        Box(modifier = Modifier.fillMaxSize().padding(3.dp)) {
          Text(
            text = "${i + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = HudTextMuted,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart),
          )
          if (def != null) {
            Text(
              text = def.shortTag,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) LanternGold else Color.White,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Clip,
              modifier = Modifier.align(Alignment.Center),
            )
            if (ammoText.isNotEmpty()) {
              Text(
                text = ammoText,
                style = MaterialTheme.typography.labelSmall,
                color = HarborCyan,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun VirtualJoystick(
  onMove: (Float, Float) -> Unit,
) {
  var knobX by remember { mutableFloatStateOf(0f) }
  var knobY by remember { mutableFloatStateOf(0f) }

  Box(
    modifier =
      Modifier.size(116.dp)
        .clip(CircleShape)
        .background(NightIndigoSurface.copy(alpha = 0.50f))
        .border(2.dp, HudBorderSubtle.copy(alpha = 0.85f), CircleShape)
        .testTag("virtual_joystick")
        .pointerInput(Unit) {
          val maxRadius = size.width * 0.36f
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            while (true) {
              val event = awaitPointerEvent()
              val ptr = event.changes.firstOrNull { it.id == down.id }
              if (ptr == null || !ptr.pressed) {
                knobX = 0f
                knobY = 0f
                onMove(0f, 0f)
                break
              }
              ptr.consume()
              val dx = ptr.position.x - center.x
              val dy = ptr.position.y - center.y
              val dist = sqrt(dx * dx + dy * dy)
              val clamped = dist.coerceAtMost(maxRadius)
              val nx = if (dist > 1f) (dx / dist) * (clamped / maxRadius) else 0f
              val ny = if (dist > 1f) (dy / dist) * (clamped / maxRadius) else 0f
              knobX = nx * maxRadius
              knobY = ny * maxRadius
              onMove(nx, ny)
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = size.width * 0.5f + knobX
      val cy = size.height * 0.5f + knobY
      drawCircle(
        color = LanternGold.copy(alpha = 0.85f),
        radius = 22.dp.toPx(),
        center = Offset(cx, cy),
      )
      drawCircle(
        color = NightAbyss,
        radius = 22.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(width = 2.dp.toPx()),
      )
    }
  }
}

@Composable
private fun HoldActionButton(
  tag: String,
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  active: Boolean,
  accent: Color,
  sizeDp: Int,
  onPressChange: (Boolean) -> Unit,
) {
  Box(
    modifier =
      Modifier.size(sizeDp.dp)
        .clip(CircleShape)
        .background(if (active) accent.copy(alpha = 0.35f) else NightIndigoSurface.copy(alpha = 0.82f))
        .border(2.dp, accent, CircleShape)
        .testTag(tag)
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            onPressChange(true)
            while (true) {
              val ev = awaitPointerEvent()
              val p = ev.changes.firstOrNull { it.id == down.id }
              if (p == null || !p.pressed) {
                onPressChange(false)
                break
              }
              p.consume()
            }
          }
        },
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size((sizeDp * 0.36f).dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = accent, fontSize = 9.sp)
    }
  }
}

@Composable
private fun TacticalTapButton(
  tag: String,
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accent: Color,
  active: Boolean,
  sizeDp: Int,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier.size(sizeDp.dp)
        .clip(CircleShape)
        .background(if (active) accent.copy(alpha = 0.38f) else NightIndigoSurface.copy(alpha = 0.84f))
        .border(1.8.dp, accent, CircleShape)
        .clickable(onClick = onClick)
        .testTag(tag),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = accent, fontSize = 8.sp)
    }
  }
}
