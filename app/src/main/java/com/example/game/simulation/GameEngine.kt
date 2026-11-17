package com.example.game.simulation

import com.example.data.BackpackSlotItem
import com.example.data.EquipmentSlot
import com.example.data.ItemCatalog
import com.example.data.ItemCategory
import com.example.data.SurvivorSave
import com.example.game.audio.SoundEngine
import com.example.game.world.TownWorld
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ZombieKind {
  WALKER,
  RUNNER,
  ARMED_RAIDER,
}

enum class ZombieAiState {
  IDLE,
  WANDER,
  CHASE,
  ATTACK,
  DEAD,
}

class ZombieEntity {
  var active = false
  var kind = ZombieKind.WALKER
  var state = ZombieAiState.IDLE
  var x = 0f
  var z = 0f
  var yawDeg = 0f
  var hp = 100f
  var maxHp = 100f
  var speed = 2.4f
  var wanderYawDeg = 0f
  var stateTimer = 0f
  var attackCooldown = 0f
  var hitFlashTimer = 0f
  var animPhase = 0f
  var deathTimer = 0f
}

class BulletTracer {
  var active = false
  var isEnemy = false
  var x0 = 0f; var y0 = 0f; var z0 = 0f
  var x1 = 0f; var y1 = 0f; var z1 = 0f
  var vx = 0f; var vy = 0f; var vz = 0f
  var life = 0f
  var maxLife = 0.08f
}

class WorldPickup {
  var active = false
  var itemId = ""
  var quantity = 1
  var x = 0f
  var z = 0f
  var isSupplyDrop = false
  var bobPhase = 0f
}

data class MinimapBlip(
  val x: Float,
  val z: Float,
  val isSupplyDrop: Boolean = false,
  val isPickup: Boolean = false,
  val isRunner: Boolean = false,
  val isArmed: Boolean = false,
)

data class HudUiState(
  val health: Float = 100f,
  val food: Float = 100f,
  val water: Float = 100f,
  val stamina: Float = 100f,
  val noiseLevel: Float = 0f,
  val playerX: Float = 0f,
  val playerZ: Float = 8f,
  val playerYawDeg: Float = 0f,
  val wave: Int = 1,
  val highestWave: Int = 1,
  val totalKills: Int = 0,
  val waveKills: Int = 0,
  val waveTargetKills: Int = 9,
  val zombiesRemaining: Int = 9,
  val restTimerSec: Float = 0f,
  val isRestPhase: Boolean = false,
  val activeHotbarSlot: Int = 0,
  val activeItemId: String = "m4_carbine",
  val magAmmo: Int = 30,
  val reserveAmmo: Int = 60,
  val isAds: Boolean = false,
  val isSprinting: Boolean = false,
  val isReloading: Boolean = false,
  val reloadProgress: Float = 0f,
  val flashlightOn: Boolean = true,
  val dayMode: Boolean = false,
  val isDead: Boolean = false,
  val toastMessage: String = "",
  val damageFlash: Float = 0f,
  val hitMarkerFlash: Float = 0f,
  val backpack: List<BackpackSlotItem> = emptyList(),
  val hotbarSlots: List<String> = listOf("m4_carbine", "glock_17", "baseball_bat", "medkit", "water_bottle", ""),
  val equipment: Map<EquipmentSlot, String> = emptyMap(),
  val currentWeightKg: Float = 0f,
  val maxWeightKg: Float = 25f,
  val armorMitigation: Float = 0f,
  val supplyDropBearingDeg: Float? = null,
  val minimapZombies: List<MinimapBlip> = emptyList(),
  val minimapLoot: List<MinimapBlip> = emptyList(),
)

/**
 * Pure Kotlin 60fps simulation engine managing player movement, weapons, ADS, 24-slot backpack,
 * object-pooled zombies and tracers, wave progression, supply drops, and survival vitals.
 */
class GameEngine(
  val world: TownWorld,
  private val sound: SoundEngine?,
) {
  companion object {
    const val MAX_ZOMBIES = 48
    const val MAX_TRACERS = 64
    const val MAX_PICKUPS = 48
    const val BACKPACK_SLOTS = 24
    const val BASE_WEIGHT_LIMIT_KG = 25f
    const val ZOMBIE_MELEE_DPS = 9f
  }

  val zombies = Array(MAX_ZOMBIES) { ZombieEntity() }
  val tracers = Array(MAX_TRACERS) { BulletTracer() }
  val pickups = Array(MAX_PICKUPS) { WorldPickup() }

  // Player transform & camera state
  var playerX = 0f
  var playerZ = 10f
  var playerYawDeg = 0f // 0 = looking North (-Z)
  var playerPitchDeg = 0f
  var walkBobPhase = 0f

  // Touch inputs (mutated from UI thread, read in step)
  @Volatile var moveInputX = 0f
  @Volatile var moveInputY = 0f
  @Volatile var sprintHeld = false
  @Volatile var fireHeld = false
  @Volatile var adsActive = false
  @Volatile var isPaused = false

  // Survival vitals & weapons
  var health = 100f
  var food = 100f
  var water = 100f
  var stamina = 100f
  var noiseLevel = 0f
  var m4Mag = 30
  var glockMag = 17
  var flashlightOn = true
  var dayMode = false

  // First-person viewmodel animation state
  var muzzleFlashTimer = 0f
  var recoilOffset = 0f
  var meleeSwingProgress = 0f
  var reloadTimer = 0f
  var reloadDuration = 1.45f
  var isReloading = false
  private var fireCooldown = 0f
  private var semiShotConsumed = false
  var hitMarkerTimer = 0f
  var damageFlashTimer = 0f

  // Wave progression
  var wave = 1
  var highestWave = 1
  var totalKills = 0
  var waveKills = 0
  var waveTarget = 9
  private var zombiesToSpawn = 9
  private var spawnTimer = 1.0f
  var isRestPhase = false
  var restTimer = 0f
  var isDead = false

  // Inventory & equipment
  val backpack = MutableList(BACKPACK_SLOTS) { BackpackSlotItem(it, "", 0) }
  val hotbar = MutableList(6) { "" }
  val equipment = mutableMapOf<EquipmentSlot, String>()
  var activeHotbarSlot = 0

  private var toastText = ""
  private var toastTimer = 0f
  private val tmpPos = FloatArray(2)
  private val rng = Random(42)
  private var onWaveClearedCallback: (() -> Unit)? = null

  init {
    resetNewRun()
  }

  fun setOnWaveClearedListener(listener: () -> Unit) {
    onWaveClearedCallback = listener
  }

  fun resetNewRun() {
    playerX = 0f
    playerZ = 11f
    playerYawDeg = 0f
    playerPitchDeg = 0f
    health = 100f
    food = 100f
    water = 100f
    stamina = 100f
    noiseLevel = 0f
    m4Mag = 30
    glockMag = 17
    flashlightOn = true
    isDead = false
    isReloading = false
    reloadTimer = 0f
    adsActive = false
    muzzleFlashTimer = 0f
    recoilOffset = 0f
    meleeSwingProgress = 0f
    hitMarkerTimer = 0f
    damageFlashTimer = 0f

    wave = 1
    totalKills = 0
    waveKills = 0
    waveTarget = 6 + 3 * wave
    zombiesToSpawn = waveTarget
    isRestPhase = false
    restTimer = 0f
    spawnTimer = 1.2f

    for (z in zombies) z.active = false
    for (t in tracers) t.active = false
    for (p in pickups) p.active = false

    // Initialize default survival loadout
    for (i in 0 until BACKPACK_SLOTS) backpack[i] = BackpackSlotItem(i, "", 0)
    equipment.clear()
    equipment[EquipmentSlot.BODY] = "coastal_jacket"
    equipment[EquipmentSlot.LEGS] = "cargo_pants"
    equipment[EquipmentSlot.FEET] = "combat_boots"

    hotbar[0] = "m4_carbine"
    hotbar[1] = "glock_17"
    hotbar[2] = "baseball_bat"
    hotbar[3] = "medkit"
    hotbar[4] = "water_bottle"
    hotbar[5] = ""
    activeHotbarSlot = 0

    addItemToBackpack("m4_carbine", 1)
    addItemToBackpack("glock_17", 1)
    addItemToBackpack("baseball_bat", 1)
    addItemToBackpack("ammo_556", 90)
    addItemToBackpack("ammo_9mm", 51)
    addItemToBackpack("water_bottle", 2)
    addItemToBackpack("canned_food", 2)
    addItemToBackpack("medkit", 2)
    addItemToBackpack("flashlight", 1)
    addItemToBackpack("knife", 1)

    spawnTownLoot()
    showToast("Wave 1: Survive Costa Oscura!")
  }

  fun applyCloudSave(save: SurvivorSave) {
    wave = save.wave.toInt().coerceAtLeast(1)
    highestWave = maxOf(highestWave, save.highestWave.toInt().coerceAtLeast(wave))
    totalKills = save.totalKills.toInt().coerceAtLeast(0)
    waveKills = save.waveKills.toInt().coerceIn(0, 6 + 3 * wave)
    health = save.health.toFloat().coerceIn(10f, 100f)
    food = save.food.toFloat().coerceIn(5f, 100f)
    water = save.water.toFloat().coerceIn(5f, 100f)
    stamina = save.stamina.toFloat().coerceIn(10f, 100f)
    activeHotbarSlot = save.activeHotbarSlot.toInt().coerceIn(0, 5)
    m4Mag = save.m4Mag.toInt().coerceIn(0, 30)
    glockMag = save.glockMag.toInt().coerceIn(0, 17)
    flashlightOn = save.flashlightOn
    dayMode = save.dayMode
    isDead = false

    for (i in 0 until BACKPACK_SLOTS) backpack[i] = BackpackSlotItem(i, "", 0)
    for (entry in save.backpackItems) {
      val idx = (entry["slotIndex"] as? Number)?.toInt() ?: continue
      val id = entry["itemId"] as? String ?: continue
      val qty = (entry["quantity"] as? Number)?.toInt() ?: 1
      if (idx in 0 until BACKPACK_SLOTS && ItemCatalog.get(id) != null && qty > 0) {
        backpack[idx] = BackpackSlotItem(idx, id, qty)
      }
    }
    for (i in 0 until 6) {
      hotbar[i] = save.hotbarSlots.getOrNull(i)?.takeIf { ItemCatalog.get(it) != null } ?: ""
    }
    equipment.clear()
    for (slot in EquipmentSlot.entries) {
      val id = save.equipment[slot.key].orEmpty()
      if (id.isNotEmpty() && ItemCatalog.get(id) != null) {
        equipment[slot] = id
      }
    }

    for (z in zombies) z.active = false
    waveTarget = 6 + 3 * wave
    val remaining = (waveTarget - waveKills).coerceAtLeast(1)
    zombiesToSpawn = remaining
    isRestPhase = false
    restTimer = 0f
    spawnTimer = 1.0f
    showToast("Loaded Cloud Save — Wave $wave")
  }

  fun toSurvivorSave(userId: String, displayName: String): SurvivorSave {
    val packedItems =
      backpack
        .filter { it.itemId.isNotEmpty() && it.quantity > 0 }
        .map {
          mapOf<String, Any>(
            "slotIndex" to it.slotIndex.toLong(),
            "itemId" to it.itemId,
            "quantity" to it.quantity.toLong(),
          )
        }
    return SurvivorSave(
      userId = userId,
      displayName = displayName,
      wave = wave.toLong(),
      highestWave = maxOf(highestWave, wave).toLong(),
      totalKills = totalKills.toLong(),
      waveKills = waveKills.toLong(),
      health = health.toDouble(),
      food = food.toDouble(),
      water = water.toDouble(),
      stamina = stamina.toDouble(),
      activeHotbarSlot = activeHotbarSlot.toLong(),
      m4Mag = m4Mag.toLong(),
      glockMag = glockMag.toLong(),
      flashlightOn = flashlightOn,
      dayMode = dayMode,
      backpackItems = packedItems,
      hotbarSlots = hotbar.toList(),
      equipment = EquipmentSlot.entries.associate { it.key to (equipment[it] ?: "") },
    )
  }

  fun addLookDelta(dx: Float, dy: Float) {
    if (isPaused || isDead) return
    val sens = if (adsActive) 0.12f else 0.22f
    playerYawDeg = (playerYawDeg + dx * sens) % 360f
    if (playerYawDeg < 0f) playerYawDeg += 360f
    playerPitchDeg = (playerPitchDeg - dy * sens).coerceIn(-68f, 68f)
  }

  fun selectHotbarSlot(slot: Int) {
    if (slot !in 0..5) return
    val itemId = hotbar[slot]
    val def = ItemCatalog.get(itemId)
    if (def != null && def.category == ItemCategory.CONSUMABLE) {
      useItemFromSlotOrId(itemId)
      return
    }
    if (def != null && def.id == "flashlight") {
      flashlightOn = !flashlightOn
      sound?.play(SoundEngine.Clip.RELOAD, 0.4f, 1.3f)
      showToast(if (flashlightOn) "Flashlight ON" else "Flashlight OFF")
      return
    }
    if (activeHotbarSlot != slot) {
      activeHotbarSlot = slot
      isReloading = false
      reloadTimer = 0f
      adsActive = false
    }
  }

  fun toggleAds() {
    val activeId = hotbar.getOrNull(activeHotbarSlot).orEmpty()
    val def = ItemCatalog.get(activeId)
    if (def?.weaponStats != null && !def.weaponStats.isMelee && !isReloading) {
      adsActive = !adsActive
    } else {
      adsActive = false
    }
  }

  fun releaseFireTrigger() {
    semiShotConsumed = false
  }

  fun startReload() {
    if (isReloading || isDead || isPaused) return
    val itemId = hotbar.getOrNull(activeHotbarSlot).orEmpty()
    val stats = ItemCatalog.get(itemId)?.weaponStats ?: return
    if (stats.isMelee || stats.ammoItemId == null) return
    val curMag = if (itemId == "m4_carbine") m4Mag else glockMag
    if (curMag >= stats.magCapacity) return
    if (countBackpackItem(stats.ammoItemId) <= 0) {
      sound?.play(SoundEngine.Clip.DRY_FIRE, 0.7f)
      showToast("No ${ItemCatalog.get(stats.ammoItemId)?.shortTag ?: "ammo"} left!")
      return
    }
    isReloading = true
    adsActive = false
    reloadDuration = if (itemId == "m4_carbine") 1.45f else 1.05f
    reloadTimer = reloadDuration
    sound?.play(SoundEngine.Clip.RELOAD, 0.8f)
  }

  fun step(dtRaw: Float) {
    if (isPaused || isDead) return
    val dt = dtRaw.coerceIn(0.001f, 0.05f)

    // Timers
    if (toastTimer > 0f) {
      toastTimer -= dt
      if (toastTimer <= 0f) toastText = ""
    }
    if (muzzleFlashTimer > 0f) muzzleFlashTimer = (muzzleFlashTimer - dt).coerceAtLeast(0f)
    if (hitMarkerTimer > 0f) hitMarkerTimer = (hitMarkerTimer - dt).coerceAtLeast(0f)
    if (damageFlashTimer > 0f) damageFlashTimer = (damageFlashTimer - dt).coerceAtLeast(0f)
    if (fireCooldown > 0f) fireCooldown = (fireCooldown - dt).coerceAtLeast(0f)
    recoilOffset = (recoilOffset - dt * 0.25f).coerceAtLeast(0f)
    noiseLevel = (noiseLevel - dt * 1.4f).coerceAtLeast(0f)

    if (meleeSwingProgress > 0f) {
      meleeSwingProgress -= dt * 3.5f
      if (meleeSwingProgress < 0f) meleeSwingProgress = 0f
    }

    // Reload completion
    if (isReloading) {
      reloadTimer -= dt
      if (reloadTimer <= 0f) {
        finishReload()
      }
    }

    // Player movement
    stepMovement(dt)

    // Survival vitals drain (Food & Water drain -> Starvation damages health)
    food = (food - dt * 0.55f).coerceAtLeast(0f)
    water = (water - dt * 0.75f).coerceAtLeast(0f)
    if (food <= 0f || water <= 0f) {
      val drain = (if (food <= 0f) 1.5f else 0f) + (if (water <= 0f) 1.8f else 0f)
      applyPlayerDamage(drain * dt, playSound = false)
    }

    // Weapon fire
    if (fireHeld) {
      tryFireOrSwing()
    } else {
      semiShotConsumed = false
    }

    // Automatic loot pickup
    stepPickups(dt)

    // Update tracers
    stepTracers(dt)

    // Wave spawner & AI
    stepWavesAndZombies(dt)
  }

  private fun stepMovement(dt: Float) {
    val mag = sqrt(moveInputX * moveInputX + moveInputY * moveInputY)
    val isMoving = mag > 0.08f
    val sprinting = sprintHeld && isMoving && !adsActive && stamina > 3f
    if (sprinting) {
      stamina = (stamina - dt * 20f).coerceAtLeast(0f)
      noiseLevel = maxOf(noiseLevel, 0.35f)
      alertZombiesInRadius(playerX, playerZ, 9.5f)
    } else {
      stamina = (stamina + dt * 15f).coerceAtMost(100f)
    }

    if (isMoving) {
      val baseSpeed =
        when {
          adsActive -> 2.75f
          sprinting -> 6.8f
          else -> 4.6f
        }
      val yawRad = playerYawDeg * (PI.toFloat() / 180f)
      val fwdX = sin(yawRad)
      val fwdZ = -cos(yawRad)
      val rightX = cos(yawRad)
      val rightZ = sin(yawRad)

      // moveInputY negative = joystick pushed UP (forward)
      val forwardAmt = -moveInputY
      val strafeAmt = moveInputX
      var vx = fwdX * forwardAmt + rightX * strafeAmt
      var vz = fwdZ * forwardAmt + rightZ * strafeAmt
      val vLen = sqrt(vx * vx + vz * vz)
      if (vLen > 1f) {
        vx /= vLen
        vz /= vLen
      }

      tmpPos[0] = playerX + vx * baseSpeed * dt
      tmpPos[1] = playerZ + vz * baseSpeed * dt
      world.resolveCircleCollision(tmpPos, 0.42f)
      playerX = tmpPos[0]
      playerZ = tmpPos[1]

      walkBobPhase += dt * (if (sprinting) 13f else if (adsActive) 6f else 9.5f)
      if (!sprinting) noiseLevel = maxOf(noiseLevel, 0.12f)
    }
  }

  private fun finishReload() {
    isReloading = false
    reloadTimer = 0f
    val itemId = hotbar.getOrNull(activeHotbarSlot).orEmpty()
    val stats = ItemCatalog.get(itemId)?.weaponStats ?: return
    val ammoId = stats.ammoItemId ?: return
    val cur = if (itemId == "m4_carbine") m4Mag else glockMag
    val needed = stats.magCapacity - cur
    if (needed <= 0) return
    val taken = consumeBackpackItem(ammoId, needed)
    if (itemId == "m4_carbine") m4Mag += taken else glockMag += taken
  }

  private fun tryFireOrSwing() {
    if (isReloading || fireCooldown > 0f) return
    val itemId = hotbar.getOrNull(activeHotbarSlot).orEmpty()
    val def = ItemCatalog.get(itemId) ?: return
    val stats = def.weaponStats ?: return

    if (!stats.isAutomatic && semiShotConsumed) return
    semiShotConsumed = true

    if (stats.isMelee) {
      fireCooldown = stats.fireRateSec
      meleeSwingProgress = 1.0f
      noiseLevel = maxOf(noiseLevel, 0.18f)
      alertZombiesInRadius(playerX, playerZ, stats.noiseRadius)
      sound?.play(SoundEngine.Clip.MELEE_SWING, 0.8f)
      performMeleeHit(stats.damage, stats.rangeMeters)
      return
    }

    val curMag = if (itemId == "m4_carbine") m4Mag else glockMag
    if (curMag <= 0) {
      fireCooldown = 0.25f
      sound?.play(SoundEngine.Clip.DRY_FIRE, 0.7f)
      startReload()
      return
    }

    if (itemId == "m4_carbine") m4Mag-- else glockMag--
    fireCooldown = stats.fireRateSec
    muzzleFlashTimer = 0.055f
    recoilOffset = (recoilOffset + stats.recoilKick).coerceAtMost(0.16f)
    playerPitchDeg = (playerPitchDeg + stats.recoilKick * 34f).coerceIn(-68f, 68f)

    noiseLevel = if (itemId == "m4_carbine") 1.0f else 0.62f
    alertZombiesInRadius(playerX, playerZ, stats.noiseRadius)
    sound?.play(
      if (itemId == "m4_carbine") SoundEngine.Clip.M4_SHOT else SoundEngine.Clip.GLOCK_SHOT,
      0.85f,
      0.95f + rng.nextFloat() * 0.1f,
    )

    fireHitscanRay(stats.damage, stats.rangeMeters, if (adsActive) stats.spreadDeg * 0.32f else stats.spreadDeg)
  }

  private fun performMeleeHit(damage: Float, range: Float) {
    val yawRad = playerYawDeg * (PI.toFloat() / 180f)
    val fx = sin(yawRad)
    val fz = -cos(yawRad)
    var bestZombie: ZombieEntity? = null
    var bestDist = range
    for (z in zombies) {
      if (!z.active || z.state == ZombieAiState.DEAD) continue
      val dx = z.x - playerX
      val dz = z.z - playerZ
      val dist = sqrt(dx * dx + dz * dz)
      if (dist <= bestDist) {
        val dot = (dx * fx + dz * fz) / maxOf(dist, 0.01f)
        if (dot > 0.45f) {
          bestDist = dist
          bestZombie = z
        }
      }
    }
    if (bestZombie != null) {
      damageZombie(bestZombie, damage)
    }
  }

  private fun fireHitscanRay(damage: Float, range: Float, spreadDeg: Float) {
    val yawJitter = (rng.nextFloat() * 2f - 1f) * spreadDeg
    val pitchJitter = (rng.nextFloat() * 2f - 1f) * spreadDeg
    val yawRad = (playerYawDeg + yawJitter) * (PI.toFloat() / 180f)
    val pitchRad = (playerPitchDeg + pitchJitter) * (PI.toFloat() / 180f)
    val cosP = cos(pitchRad)
    val dx = sin(yawRad) * cosP
    val dy = sin(pitchRad)
    val dz = -cos(yawRad) * cosP

    val ox = playerX
    val oy = 1.62f
    val oz = playerZ

    val wallDist = world.raycastEnvironment(ox, oy, oz, dx, dy, dz, range)

    // Check zombies along ray (cylinder/capsule hit test)
    var hitZombie: ZombieEntity? = null
    var hitDist = wallDist
    var hitHead = false

    for (z in zombies) {
      if (!z.active || z.state == ZombieAiState.DEAD) continue
      val toZx = z.x - ox
      val toZz = z.z - oz
      val proj = toZx * dx + toZz * dz
      if (proj < 0.3f || proj > hitDist) continue
      val rx = ox + dx * proj
      val ry = oy + dy * proj
      val rz = oz + dz * proj
      val radialSq = (rx - z.x) * (rx - z.x) + (rz - z.z) * (rz - z.z)
      if (radialSq <= 0.42f * 0.42f && ry in 0.05f..1.92f) {
        hitDist = proj
        hitZombie = z
        hitHead = ry >= 1.44f
      }
    }

    val endX = ox + dx * hitDist
    val endY = oy + dy * hitDist
    val endZ = oz + dz * hitDist
    spawnTracer(ox + dx * 0.6f, oy - 0.12f, oz + dz * 0.6f, endX, endY, endZ, isEnemy = false)

    if (hitZombie != null) {
      val dmg = if (hitHead) damage * 1.65f else damage
      damageZombie(hitZombie, dmg)
    }
  }

  private fun damageZombie(z: ZombieEntity, dmg: Float) {
    z.hp -= dmg
    z.hitFlashTimer = 0.12f
    z.state = ZombieAiState.CHASE
    hitMarkerTimer = 0.12f
    sound?.play(SoundEngine.Clip.ZOMBIE_HIT, 0.75f, 0.9f + rng.nextFloat() * 0.2f)
    if (z.hp <= 0f) {
      z.hp = 0f
      z.state = ZombieAiState.DEAD
      z.deathTimer = 0.75f
      waveKills++
      totalKills++
      if (rng.nextFloat() < 0.42f) {
        val dropPool = listOf("ammo_556", "ammo_9mm", "water_bottle", "canned_food", "medkit")
        val id = dropPool[rng.nextInt(dropPool.size)]
        val qty = if (id.startsWith("ammo_")) 12 else 1
        spawnPickup(id, qty, z.x, z.z, isSupplyDrop = false)
      }
    }
  }

  private fun spawnTracer(
    x0: Float, y0: Float, z0: Float,
    x1: Float, y1: Float, z1: Float,
    isEnemy: Boolean,
  ) {
    for (t in tracers) {
      if (!t.active) {
        t.active = true
        t.isEnemy = isEnemy
        t.x0 = x0; t.y0 = y0; t.z0 = z0
        t.x1 = x1; t.y1 = y1; t.z1 = z1
        t.maxLife = if (isEnemy) 0.55f else 0.07f
        t.life = t.maxLife
        if (isEnemy) {
          val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
          val len = maxOf(sqrt(dx * dx + dy * dy + dz * dz), 0.1f)
          t.vx = (dx / len) * 26f
          t.vy = (dy / len) * 26f
          t.vz = (dz / len) * 26f
          t.x1 = x0 + (dx / len) * 0.9f
          t.y1 = y0 + (dy / len) * 0.9f
          t.z1 = z0 + (dz / len) * 0.9f
        }
        return
      }
    }
  }

  private fun stepTracers(dt: Float) {
    for (t in tracers) {
      if (!t.active) continue
      t.life -= dt
      if (t.isEnemy) {
        t.x0 += t.vx * dt; t.y0 += t.vy * dt; t.z0 += t.vz * dt
        t.x1 = t.x0 + t.vx * 0.04f; t.y1 = t.y0 + t.vy * 0.04f; t.z1 = t.z0 + t.vz * 0.04f
        val dSq = (t.x0 - playerX) * (t.x0 - playerX) + (t.z0 - playerZ) * (t.z0 - playerZ)
        if (dSq < 0.65f * 0.65f) {
          applyPlayerDamage(7.5f, playSound = true)
          t.active = false
          continue
        }
      }
      if (t.life <= 0f) t.active = false
    }
  }

  private fun stepWavesAndZombies(dt: Float) {
    if (isRestPhase) {
      restTimer -= dt
      if (restTimer <= 0f) {
        isRestPhase = false
        wave++
        highestWave = maxOf(highestWave, wave)
        waveKills = 0
        waveTarget = 6 + 3 * wave
        zombiesToSpawn = waveTarget
        spawnTimer = 0.8f
        sound?.play(SoundEngine.Clip.WAVE_HORN, 0.7f)
        showToast("Wave $wave Started! ($waveTarget hostiles)")
      }
      return
    }

    // Spawn queued zombies up to pool capacity
    val activeAlive = zombies.count { it.active && it.state != ZombieAiState.DEAD }
    if (zombiesToSpawn > 0 && activeAlive < 24) {
      spawnTimer -= dt
      if (spawnTimer <= 0f) {
        spawnNextZombie()
        zombiesToSpawn--
        spawnTimer = (1.35f - wave * 0.06f).coerceAtLeast(0.45f)
      }
    }

    // Update active zombies
    for (z in zombies) {
      if (!z.active) continue
      if (z.hitFlashTimer > 0f) z.hitFlashTimer = (z.hitFlashTimer - dt).coerceAtLeast(0f)

      if (z.state == ZombieAiState.DEAD) {
        z.deathTimer -= dt
        if (z.deathTimer <= 0f) z.active = false
        continue
      }

      val dx = playerX - z.x
      val dz = playerZ - z.z
      val dist = sqrt(dx * dx + dz * dz)
      val canSee = dist < 28f && world.hasLineOfSight(z.x, z.z, playerX, playerZ)

      if (canSee) {
        z.state = if (dist < 1.65f) ZombieAiState.ATTACK else ZombieAiState.CHASE
      } else if (z.state != ZombieAiState.CHASE) {
        z.stateTimer -= dt
        if (z.stateTimer <= 0f) {
          z.state = if (rng.nextFloat() < 0.65f) ZombieAiState.WANDER else ZombieAiState.IDLE
          z.wanderYawDeg = rng.nextFloat() * 360f
          z.stateTimer = 2.0f + rng.nextFloat() * 2.5f
        }
      }

      when (z.state) {
        ZombieAiState.IDLE -> Unit
        ZombieAiState.WANDER -> {
          val rad = z.wanderYawDeg * (PI.toFloat() / 180f)
          tmpPos[0] = z.x + sin(rad) * (z.speed * 0.45f) * dt
          tmpPos[1] = z.z + cos(rad) * (z.speed * 0.45f) * dt
          world.resolveCircleCollision(tmpPos, 0.40f)
          z.x = tmpPos[0]
          z.z = tmpPos[1]
          z.yawDeg = z.wanderYawDeg
          z.animPhase += dt * 4.5f
        }
        ZombieAiState.CHASE -> {
          val targetYaw = atan2(dx, dz) * (180f / PI.toFloat())
          z.yawDeg = targetYaw
          if (z.kind == ZombieKind.ARMED_RAIDER && dist in 7f..19f && canSee) {
            z.attackCooldown -= dt
            if (z.attackCooldown <= 0f) {
              z.attackCooldown = 2.1f
              sound?.play(SoundEngine.Clip.GLOCK_SHOT, 0.55f, 0.82f)
              spawnTracer(z.x, 1.35f, z.z, playerX, 1.35f, playerZ, isEnemy = true)
            }
          } else if (dist > 1.45f) {
            val nx = dx / maxOf(dist, 0.01f)
            val nz = dz / maxOf(dist, 0.01f)
            tmpPos[0] = z.x + nx * z.speed * dt
            tmpPos[1] = z.z + nz * z.speed * dt
            world.resolveCircleCollision(tmpPos, 0.40f)
            z.x = tmpPos[0]
            z.z = tmpPos[1]
            z.animPhase += dt * (z.speed * 2.4f)
          } else {
            z.state = ZombieAiState.ATTACK
          }
        }
        ZombieAiState.ATTACK -> {
          z.yawDeg = atan2(dx, dz) * (180f / PI.toFloat())
          if (dist > 1.95f) {
            z.state = ZombieAiState.CHASE
          } else {
            applyPlayerDamage(ZOMBIE_MELEE_DPS * dt, playSound = z.attackCooldown <= 0f)
            if (z.attackCooldown <= 0f) {
              z.attackCooldown = 0.85f
            } else {
              z.attackCooldown -= dt
            }
          }
        }
        ZombieAiState.DEAD -> Unit
      }
    }

    // Check wave completion
    val remainingAlive = zombies.count { it.active && it.state != ZombieAiState.DEAD }
    if (zombiesToSpawn <= 0 && remainingAlive == 0) {
      isRestPhase = true
      restTimer = 6.0f
      highestWave = maxOf(highestWave, wave + 1)
      spawnSupplyDrop()
      spawnTownLoot()
      onWaveClearedCallback?.invoke()
      showToast("Wave $wave Cleared! Supply Drop in Plaza (6s rest)")
    }
  }

  private fun spawnNextZombie() {
    val slot = zombies.firstOrNull { !it.active } ?: return
    val candidates = world.spawnPoints.filter { (sx, sz) ->
      val dx = sx - playerX
      val dz = sz - playerZ
      dx * dx + dz * dz > 144f // at least 12m from player
    }
    val (sx, sz) = (candidates.ifEmpty { world.spawnPoints }).random(rng)
    val roll = rng.nextFloat()
    val kind =
      when {
        wave >= 3 && roll < 0.22f -> ZombieKind.ARMED_RAIDER
        wave >= 2 && roll < 0.52f -> ZombieKind.RUNNER
        else -> ZombieKind.WALKER
      }

    slot.active = true
    slot.kind = kind
    slot.state = ZombieAiState.CHASE
    slot.x = sx + (rng.nextFloat() - 0.5f) * 2f
    slot.z = sz + (rng.nextFloat() - 0.5f) * 2f
    slot.yawDeg = rng.nextFloat() * 360f
    slot.maxHp =
      when (kind) {
        ZombieKind.WALKER -> 85f + wave * 10f
        ZombieKind.RUNNER -> 65f + wave * 8f
        ZombieKind.ARMED_RAIDER -> 120f + wave * 12f
      }
    slot.hp = slot.maxHp
    slot.speed =
      when (kind) {
        ZombieKind.WALKER -> 2.35f + minOf(wave * 0.08f, 0.8f)
        ZombieKind.RUNNER -> 4.35f + minOf(wave * 0.08f, 0.9f)
        ZombieKind.ARMED_RAIDER -> 2.65f
      }
    slot.attackCooldown = 0.6f
    slot.hitFlashTimer = 0f
    slot.animPhase = rng.nextFloat() * 6f
  }

  private fun alertZombiesInRadius(ox: Float, oz: Float, radius: Float) {
    val rSq = radius * radius
    for (z in zombies) {
      if (!z.active || z.state == ZombieAiState.DEAD) continue
      val dx = z.x - ox
      val dz = z.z - oz
      if (dx * dx + dz * dz <= rSq) {
        z.state = ZombieAiState.CHASE
      }
    }
  }

  private fun applyPlayerDamage(rawDmg: Float, playSound: Boolean) {
    val mitigation = computeArmorMitigation()
    val dmg = rawDmg * (1f - mitigation)
    health = (health - dmg).coerceAtLeast(0f)
    damageFlashTimer = 0.25f
    if (playSound) sound?.play(SoundEngine.Clip.PLAYER_HURT, 0.7f)
    if (health <= 0f) {
      isDead = true
      fireHeld = false
    }
  }

  private fun spawnTownLoot() {
    val lootTable =
      listOf(
        "ammo_556" to 20,
        "ammo_9mm" to 17,
        "water_bottle" to 1,
        "canned_food" to 1,
        "medkit" to 1,
        "kevlar_vest" to 1,
        "hiking_pack" to 1,
        "tactical_cap" to 1,
      )
    for (sp in world.lootSpawns) {
      val alreadyNear = pickups.any { it.active && (it.x - sp.x) * (it.x - sp.x) + (it.z - sp.z) * (it.z - sp.z) < 2f }
      if (!alreadyNear) {
        val (id, qty) = lootTable[rng.nextInt(lootTable.size)]
        spawnPickup(id, qty, sp.x, sp.z, isSupplyDrop = false)
      }
    }
  }

  private fun spawnSupplyDrop() {
    val dropSpots = listOf(0f to -2f, 0f to -36f, -3f to 16f)
    val (dx, dz) = dropSpots[wave % dropSpots.size]
    spawnPickup("ammo_556", 45, dx - 0.7f, dz, isSupplyDrop = true)
    spawnPickup("medkit", 2, dx + 0.7f, dz, isSupplyDrop = true)
    spawnPickup("canned_food", 2, dx, dz + 0.8f, isSupplyDrop = true)
    spawnPickup("water_bottle", 2, dx, dz - 0.8f, isSupplyDrop = true)
  }

  private fun spawnPickup(itemId: String, qty: Int, x: Float, z: Float, isSupplyDrop: Boolean) {
    val slot = pickups.firstOrNull { !it.active } ?: return
    slot.active = true
    slot.itemId = itemId
    slot.quantity = qty
    slot.x = x
    slot.z = z
    slot.isSupplyDrop = isSupplyDrop
    slot.bobPhase = rng.nextFloat() * 6.28f
  }

  private fun stepPickups(dt: Float) {
    for (p in pickups) {
      if (!p.active) continue
      p.bobPhase += dt * 3.2f
      val dx = p.x - playerX
      val dz = p.z - playerZ
      if (dx * dx + dz * dz < 1.65f * 1.65f) {
        if (addItemToBackpack(p.itemId, p.quantity)) {
          val name = ItemCatalog.get(p.itemId)?.name ?: p.itemId
          sound?.play(SoundEngine.Clip.PICKUP, 0.75f)
          showToast("+${p.quantity} $name")
          p.active = false
        } else {
          showToast("Backpack Full / Overweight!")
        }
      }
    }
  }

  fun computeCurrentWeightKg(): Float {
    var total = 0f
    for (b in backpack) {
      if (b.itemId.isNotEmpty() && b.quantity > 0) {
        total += (ItemCatalog.get(b.itemId)?.weightKg ?: 0f) * b.quantity
      }
    }
    for (id in equipment.values) {
      total += (ItemCatalog.get(id)?.weightKg ?: 0f) * 0.5f // worn gear weighs half
    }
    return total
  }

  fun computeMaxWeightKg(): Float {
    val backId = equipment[EquipmentSlot.BACK].orEmpty()
    val bonus = ItemCatalog.get(backId)?.extraWeightKg ?: 0f
    return BASE_WEIGHT_LIMIT_KG + bonus
  }

  fun computeArmorMitigation(): Float {
    var sum = 0f
    for (id in equipment.values) {
      sum += ItemCatalog.get(id)?.armorBonus ?: 0f
    }
    return sum.coerceAtMost(0.65f)
  }

  fun addItemToBackpack(itemId: String, quantity: Int): Boolean {
    val def = ItemCatalog.get(itemId) ?: return false
    val addWeight = def.weightKg * quantity
    if (computeCurrentWeightKg() + addWeight > computeMaxWeightKg() + 0.01f) {
      return false
    }
    var remaining = quantity
    if (def.maxStack > 1) {
      for (i in 0 until BACKPACK_SLOTS) {
        val cur = backpack[i]
        if (cur.itemId == itemId && cur.quantity < def.maxStack) {
          val space = def.maxStack - cur.quantity
          val add = minOf(space, remaining)
          backpack[i] = cur.copy(quantity = cur.quantity + add)
          remaining -= add
          if (remaining <= 0) return true
        }
      }
    }
    for (i in 0 until BACKPACK_SLOTS) {
      if (backpack[i].itemId.isEmpty()) {
        val put = minOf(def.maxStack, remaining)
        backpack[i] = BackpackSlotItem(i, itemId, put)
        remaining -= put
        if (remaining <= 0) return true
      }
    }
    return remaining == 0
  }

  fun countBackpackItem(itemId: String): Int {
    var sum = 0
    for (b in backpack) {
      if (b.itemId == itemId) sum += b.quantity
    }
    return sum
  }

  fun consumeBackpackItem(itemId: String, count: Int): Int {
    var needed = count
    for (i in 0 until BACKPACK_SLOTS) {
      val b = backpack[i]
      if (b.itemId == itemId && b.quantity > 0) {
        val take = minOf(b.quantity, needed)
        val left = b.quantity - take
        backpack[i] = if (left <= 0) BackpackSlotItem(i, "", 0) else b.copy(quantity = left)
        needed -= take
        if (needed <= 0) break
      }
    }
    return count - needed
  }

  fun useBackpackSlot(slotIndex: Int) {
    if (slotIndex !in 0 until BACKPACK_SLOTS) return
    val entry = backpack[slotIndex]
    val def = ItemCatalog.get(entry.itemId) ?: return
    when (def.category) {
      ItemCategory.CONSUMABLE -> {
        health = (health + def.healAmount).coerceAtMost(100f)
        food = (food + def.foodAmount).coerceAtMost(100f)
        water = (water + def.waterAmount).coerceAtMost(100f)
        val left = entry.quantity - 1
        backpack[slotIndex] = if (left <= 0) BackpackSlotItem(slotIndex, "", 0) else entry.copy(quantity = left)
        sound?.play(SoundEngine.Clip.PICKUP, 0.7f, 1.15f)
        showToast("Used ${def.name}")
      }
      ItemCategory.GEAR -> {
        val eqSlot = def.equipSlot ?: return
        val previous = equipment[eqSlot].orEmpty()
        equipment[eqSlot] = def.id
        backpack[slotIndex] =
          if (previous.isNotEmpty()) BackpackSlotItem(slotIndex, previous, 1)
          else BackpackSlotItem(slotIndex, "", 0)
        sound?.play(SoundEngine.Clip.RELOAD, 0.6f, 1.1f)
        showToast("Equipped ${def.name}")
      }
      ItemCategory.UTILITY -> {
        if (def.id == "flashlight") {
          flashlightOn = !flashlightOn
          showToast(if (flashlightOn) "Flashlight ON" else "Flashlight OFF")
        }
      }
      ItemCategory.WEAPON -> {
        val emptyHotbar = hotbar.indexOfFirst { it.isEmpty() }
        val target = if (emptyHotbar >= 0) emptyHotbar else activeHotbarSlot
        hotbar[target] = def.id
        activeHotbarSlot = target
        showToast("Equipped ${def.name} to Slot ${target + 1}")
      }
      ItemCategory.AMMO -> {
        if (def.id == "ammo_556") {
          activeHotbarSlot = hotbar.indexOf("m4_carbine").takeIf { it >= 0 } ?: 0
          startReload()
        } else if (def.id == "ammo_9mm") {
          activeHotbarSlot = hotbar.indexOf("glock_17").takeIf { it >= 0 } ?: 1
          startReload()
        }
      }
    }
  }

  private fun useItemFromSlotOrId(itemId: String) {
    val idx = backpack.indexOfFirst { it.itemId == itemId && it.quantity > 0 }
    if (idx >= 0) {
      useBackpackSlot(idx)
    } else {
      showToast("Out of ${ItemCatalog.get(itemId)?.name ?: itemId}")
    }
  }

  fun dropBackpackSlot(slotIndex: Int) {
    if (slotIndex !in 0 until BACKPACK_SLOTS) return
    val entry = backpack[slotIndex]
    if (entry.itemId.isEmpty()) return
    spawnPickup(entry.itemId, entry.quantity, playerX + 0.9f, playerZ + 0.9f, isSupplyDrop = false)
    backpack[slotIndex] = BackpackSlotItem(slotIndex, "", 0)
    showToast("Dropped ${ItemCatalog.get(entry.itemId)?.name ?: entry.itemId}")
  }

  fun assignBackpackItemToHotbar(backpackSlot: Int, hotbarIndex: Int) {
    if (backpackSlot !in 0 until BACKPACK_SLOTS || hotbarIndex !in 0..5) return
    val itemId = backpack[backpackSlot].itemId
    if (itemId.isEmpty()) return
    hotbar[hotbarIndex] = itemId
    showToast("Assigned ${ItemCatalog.get(itemId)?.shortTag ?: itemId} -> Slot ${hotbarIndex + 1}")
  }

  fun showToast(msg: String) {
    toastText = msg
    toastTimer = 2.4f
  }

  fun snapshotHudState(): HudUiState {
    val activeId = hotbar.getOrNull(activeHotbarSlot).orEmpty()
    val magAmmo =
      when (activeId) {
        "m4_carbine" -> m4Mag
        "glock_17" -> glockMag
        else -> 0
      }
    val reserveAmmo =
      when (activeId) {
        "m4_carbine" -> countBackpackItem("ammo_556")
        "glock_17" -> countBackpackItem("ammo_9mm")
        else -> 0
      }

    val supplyPickup = pickups.firstOrNull { it.active && it.isSupplyDrop }
    val supplyBearing =
      supplyPickup?.let {
        val deg = atan2(it.x - playerX, -(it.z - playerZ)) * (180f / PI.toFloat())
        (deg + 360f) % 360f
      }

    val zBlips =
      zombies
        .filter { it.active && it.state != ZombieAiState.DEAD }
        .map {
          MinimapBlip(
            x = it.x,
            z = it.z,
            isRunner = it.kind == ZombieKind.RUNNER,
            isArmed = it.kind == ZombieKind.ARMED_RAIDER,
          )
        }
    val lBlips =
      pickups
        .filter { it.active }
        .map { MinimapBlip(x = it.x, z = it.z, isSupplyDrop = it.isSupplyDrop, isPickup = !it.isSupplyDrop) }

    val remaining = zombies.count { it.active && it.state != ZombieAiState.DEAD } + zombiesToSpawn

    return HudUiState(
      health = health,
      food = food,
      water = water,
      stamina = stamina,
      noiseLevel = noiseLevel,
      playerX = playerX,
      playerZ = playerZ,
      playerYawDeg = playerYawDeg,
      wave = wave,
      highestWave = highestWave,
      totalKills = totalKills,
      waveKills = waveKills,
      waveTargetKills = waveTarget,
      zombiesRemaining = remaining,
      restTimerSec = restTimer.coerceAtLeast(0f),
      isRestPhase = isRestPhase,
      activeHotbarSlot = activeHotbarSlot,
      activeItemId = activeId,
      magAmmo = magAmmo,
      reserveAmmo = reserveAmmo,
      isAds = adsActive,
      isSprinting = sprintHeld,
      isReloading = isReloading,
      reloadProgress = if (isReloading) 1f - (reloadTimer / reloadDuration).coerceIn(0f, 1f) else 0f,
      flashlightOn = flashlightOn,
      dayMode = dayMode,
      isDead = isDead,
      toastMessage = toastText,
      damageFlash = (damageFlashTimer / 0.25f).coerceIn(0f, 1f),
      hitMarkerFlash = (hitMarkerTimer / 0.12f).coerceIn(0f, 1f),
      backpack = backpack.toList(),
      hotbarSlots = hotbar.toList(),
      equipment = equipment.toMap(),
      currentWeightKg = computeCurrentWeightKg(),
      maxWeightKg = computeMaxWeightKg(),
      armorMitigation = computeArmorMitigation(),
      supplyDropBearingDeg = supplyBearing,
      minimapZombies = zBlips,
      minimapLoot = lBlips,
    )
  }
}
