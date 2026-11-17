package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.EquipmentSlot
import com.example.data.ItemCatalog
import com.example.data.ItemCategory
import com.example.game.simulation.HudUiState
import com.example.ui.theme.HarborCyan
import com.example.ui.theme.HudBorderSubtle
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.HungerAmber
import com.example.ui.theme.LanternGold
import com.example.ui.theme.NightAbyss
import com.example.ui.theme.NightIndigoContainer
import com.example.ui.theme.NightIndigoSurface
import com.example.ui.theme.ThreatCrimson
import com.example.ui.theme.VitalityLime

@Composable
fun InventoryOverlay(
  state: HudUiState,
  onClose: () -> Unit,
  onUseSlot: (Int) -> Unit,
  onDropSlot: (Int) -> Unit,
  onAssignHotbar: (backpackSlot: Int, hotbarIndex: Int) -> Unit,
) {
  var selectedSlot by remember { mutableIntStateOf(0) }
  var draggingSlot by remember { mutableIntStateOf(-1) }
  var dragPointerRoot by remember { mutableStateOf(Offset.Zero) }
  val hotbarBounds = remember { mutableMapOf<Int, androidx.compose.ui.geometry.Rect>() }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(NightAbyss.copy(alpha = 0.88f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(14.dp),
  ) {
    Surface(
      color = NightIndigoSurface,
      shape = RoundedCornerShape(16.dp),
      modifier =
        Modifier.fillMaxSize()
          .border(1.5.dp, HudBorderSubtle, RoundedCornerShape(16.dp)),
    ) {
      Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Header Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SURVIVOR BACKPACK", style = MaterialTheme.typography.headlineMedium, color = LanternGold)
            WeightIndicator(currentKg = state.currentWeightKg, maxKg = state.maxWeightKg)
          }
          IconButton(
            onClick = onClose,
            modifier = Modifier.size(48.dp).testTag("close_inventory_button"),
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close backpack", tint = Color.White)
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main 3-Pane Split: Paper-Doll | 24-Slot Backpack + Hotbar | Detail Panel
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // LEFT: Paper-Doll Equipment
          PaperDollPanel(
            state = state,
            modifier = Modifier.weight(0.72f).fillMaxHeight(),
          )

          // CENTER: 24-Slot Backpack Grid & Drag-to-Hotbar Targets
          Column(
            modifier = Modifier.weight(1.35f).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "24-SLOT PACK (TAP TO INSPECT · DRAG TO HOTBAR)",
              style = MaterialTheme.typography.labelSmall,
              color = HudTextMuted,
            )
            LazyVerticalGrid(
              columns = GridCells.Fixed(6),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            ) {
              items(24) { index ->
                val entry = state.backpack.getOrNull(index)
                val itemId = entry?.itemId.orEmpty()
                val def = ItemCatalog.get(itemId)
                val isSelected = selectedSlot == index
                var cellOrigin by remember { mutableStateOf(Offset.Zero) }
                Box(
                  modifier =
                    Modifier.height(50.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (isSelected) LanternGold.copy(alpha = 0.22f) else NightIndigoContainer)
                      .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) LanternGold else HudBorderSubtle,
                        shape = RoundedCornerShape(8.dp),
                      )
                      .onGloballyPositioned { coords ->
                        cellOrigin = coords.boundsInRoot().topLeft
                      }
                      .clickable { selectedSlot = index }
                      .pointerInput(index, itemId) {
                        if (itemId.isNotEmpty()) {
                          detectDragGestures(
                            onDragStart = { localOffset ->
                              draggingSlot = index
                              dragPointerRoot = cellOrigin + localOffset
                            },
                            onDrag = { change, dragAmount ->
                              change.consume()
                              dragPointerRoot += dragAmount
                            },
                            onDragEnd = {
                              val targetHotbar =
                                hotbarBounds.entries.firstOrNull { (_, rect) ->
                                  rect.contains(dragPointerRoot)
                                }?.key
                              if (targetHotbar != null) {
                                onAssignHotbar(index, targetHotbar)
                              }
                              draggingSlot = -1
                            },
                            onDragCancel = { draggingSlot = -1 },
                          )
                        }
                      }
                      .testTag("backpack_slot_$index")
                      .padding(4.dp),
                ) {
                  if (def != null && entry != null) {
                    Text(
                      text = def.shortTag,
                      style = MaterialTheme.typography.labelSmall,
                      color = Color.White,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Clip,
                      modifier = Modifier.align(Alignment.Center),
                    )
                    if (entry.quantity > 1) {
                      Text(
                        text = "x${entry.quantity}",
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

            // Hotbar Drop Strip
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              for (hi in 0 until 6) {
                val hId = state.hotbarSlots.getOrNull(hi).orEmpty()
                val hDef = ItemCatalog.get(hId)
                val hovered =
                  draggingSlot >= 0 && (hotbarBounds[hi]?.contains(dragPointerRoot) == true)
                Box(
                  modifier =
                    Modifier.weight(1f)
                      .height(44.dp)
                      .onGloballyPositioned { coords ->
                        hotbarBounds[hi] = coords.boundsInRoot()
                      }
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (hovered) HarborCyan.copy(alpha = 0.25f) else NightAbyss)
                      .border(1.5.dp, if (hovered) HarborCyan else HudBorderSubtle, RoundedCornerShape(8.dp))
                      .clickable {
                        if (state.backpack.getOrNull(selectedSlot)?.itemId?.isNotEmpty() == true) {
                          onAssignHotbar(selectedSlot, hi)
                        }
                      },
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = hDef?.shortTag ?: "SLOT ${hi + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hDef != null) LanternGold else HudTextMuted,
                    fontSize = 10.sp,
                  )
                }
              }
            }
          }

          // RIGHT: Item Detail Panel
          ItemDetailPanel(
            state = state,
            selectedSlot = selectedSlot,
            onUse = { onUseSlot(selectedSlot) },
            onDrop = { onDropSlot(selectedSlot) },
            modifier = Modifier.weight(0.95f).fillMaxHeight(),
          )
        }
      }
    }
  }
}

@Composable
private fun WeightIndicator(currentKg: Float, maxKg: Float) {
  val over = currentKg >= maxKg * 0.9f
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "${"%.1f".format(currentKg)} / ${"%.1f".format(maxKg)} kg",
      style = MaterialTheme.typography.labelMedium,
      color = if (over) HungerAmber else HarborCyan,
    )
    Box(
      modifier =
        Modifier.width(96.dp)
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(NightAbyss),
    ) {
      Box(
        modifier =
          Modifier.fillMaxHeight()
            .fillMaxWidth((currentKg / maxKg).coerceIn(0f, 1f))
            .background(if (over) HungerAmber else VitalityLime),
      )
    }
  }
}

@Composable
private fun PaperDollPanel(state: HudUiState, modifier: Modifier = Modifier) {
  Surface(
    color = NightAbyss.copy(alpha = 0.65f),
    shape = RoundedCornerShape(12.dp),
    modifier = modifier.border(1.dp, HudBorderSubtle, RoundedCornerShape(12.dp)),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(10.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Shield, contentDescription = "Armor rating", tint = VitalityLime, modifier = Modifier.size(16.dp))
        Text(
          text = "ARMOR ${(state.armorMitigation * 100).toInt()}%",
          style = MaterialTheme.typography.labelMedium,
          color = VitalityLime,
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (slot in EquipmentSlot.entries) {
          val eqId = state.equipment[slot].orEmpty()
          val eqDef = ItemCatalog.get(eqId)
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(NightIndigoContainer)
                .border(1.dp, HudBorderSubtle, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(slot.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HudTextMuted)
            Text(
              text = eqDef?.shortTag ?: "EMPTY",
              style = MaterialTheme.typography.labelSmall,
              color = if (eqDef != null) LanternGold else HudTextMuted.copy(alpha = 0.5f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ItemDetailPanel(
  state: HudUiState,
  selectedSlot: Int,
  onUse: () -> Unit,
  onDrop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val entry = state.backpack.getOrNull(selectedSlot)
  val def = ItemCatalog.get(entry?.itemId.orEmpty())

  Surface(
    color = NightAbyss.copy(alpha = 0.65f),
    shape = RoundedCornerShape(12.dp),
    modifier = modifier.border(1.dp, HudBorderSubtle, RoundedCornerShape(12.dp)),
  ) {
    if (def == null || entry == null || entry.quantity <= 0) {
      Box(modifier = Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
        Text(
          text = "Select an item slot to inspect stats, equip gear, or reload.",
          style = MaterialTheme.typography.bodyMedium,
          color = HudTextMuted,
        )
      }
    } else {
      Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(def.name, style = MaterialTheme.typography.titleMedium, color = LanternGold)
          Text(
            text = "${def.category.name} · ${"%.2f".format(def.weightKg * entry.quantity)} kg (x${entry.quantity})",
            style = MaterialTheme.typography.labelSmall,
            color = HarborCyan,
          )
          Text(def.description, style = MaterialTheme.typography.bodyMedium, color = Color.White)

          val ws = def.weaponStats
          if (ws != null) {
            Text(
              text = "DMG ${ws.damage.toInt()} · RNG ${ws.rangeMeters.toInt()}m · NOISE ${ws.noiseRadius.toInt()}m",
              style = MaterialTheme.typography.labelSmall,
              color = VitalityLime,
            )
          }
          if (def.armorBonus > 0f) {
            Text(
              text = "Armor Mitigation: +${(def.armorBonus * 100).toInt()}%",
              style = MaterialTheme.typography.labelSmall,
              color = VitalityLime,
            )
          }
          if (def.extraWeightKg > 0f) {
            Text(
              text = "Carry Limit Bonus: +${def.extraWeightKg} kg",
              style = MaterialTheme.typography.labelSmall,
              color = VitalityLime,
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          val primaryLabel =
            when (def.category) {
              ItemCategory.GEAR, ItemCategory.WEAPON -> "EQUIP"
              ItemCategory.AMMO -> "RELOAD"
              else -> "USE"
            }
          Button(
            onClick = onUse,
            colors = ButtonDefaults.buttonColors(containerColor = LanternGold, contentColor = NightAbyss),
            modifier = Modifier.weight(1f).height(48.dp).testTag("inventory_use_button"),
          ) {
            Text(primaryLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          }
          OutlinedButton(
            onClick = onDrop,
            modifier = Modifier.height(48.dp).testTag("inventory_drop_button"),
          ) {
            Icon(Icons.Default.Delete, contentDescription = "Drop item", tint = ThreatCrimson, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("DROP", style = MaterialTheme.typography.labelSmall, color = ThreatCrimson)
          }
        }
      }
    }
  }
}
